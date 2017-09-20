package xyz.jetdrone.vertx.hot.reload.impl;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import xyz.jetdrone.vertx.hot.reload.HotReload;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class HotReloadImpl implements HotReload {

  // language=JavaScript
  private static final String SCRIPT =
    "(function () {\n" +
      "    var lastUpdate = -1;\n" +
      "    var forceReload = false;\n" +
      "\n" +
      "    var load = function () {\n" +
      "      var xobj = new XMLHttpRequest();\n" +
      "      xobj.overrideMimeType(\"application/json\");\n" +
      "      xobj.open('GET', \"/hot-reload\", true);\n" +
      "      xobj.onreadystatechange = function () {\n" +
      "        if (xobj.readyState === 4) {\n" +
      "          if (xobj.status != \"200\") {\n" +
      "            forceReload = true;\n" +
      "            // recheck in a second\n" +
      "            setTimeout(load, 1000);\n" +
      "          } else {\n" +
      "            var json = JSON.parse(xobj.responseText);\n" +
      "            if (lastUpdate === -1) {\n" +
      "              lastUpdate = json.uuid;\n" +
      "            }\n" +
      "            if (forceReload || lastUpdate !== json.uuid) {\n" +
      "              window.location.reload();\n" +
      "            } else {\n" +
      "              // recheck in a second\n" +
      "              setTimeout(load, 1000);\n" +
      "            }\n" +
      "          }\n" +
      "        }\n" +
      "      };\n" +
      "      xobj.send(null);\n" +
      "    };\n" +
      "\n" +
      "    load();\n" +
      "  })();";

  // language=JavaScript
  private static final String SSE_SCRIPT =
    "(function () {\n" +
      "  var lastUpdate = -1;\n" +
      "  var load = function () {\n" +
      "    var hotReloadSSE = new EventSource(\"/hot-reload\");\n" +
      "\n" +
      "    hotReloadSSE.addEventListener(\"reload\", function (e) {\n" +
      "      var json = JSON.parse(e.data);\n" +
      "      if (lastUpdate === -1) {\n" +
      "        lastUpdate = json.uuid;\n" +
      "      }\n" +
      "      if (lastUpdate !== json.uuid) {\n" +
      "        window.location.reload();\n" +
      "      }\n" +
      "    }, false);\n" +
      "\n" +
      "    hotReloadSSE.onerror = function () {\n" +
      "      // recheck in a second\n" +
      "      setTimeout(load, 1000);\n" +
      "    }\n" +
      "  };\n" +
      "  load();\n" +
      "})();\n";

  private final Logger log = LoggerFactory.getLogger(HotReloadImpl.class);

  private final AtomicReference<String> payload = new AtomicReference<>();
  private final boolean sse;
  private final Set<HttpServerResponse> clients;

  public HotReloadImpl(boolean sse) {
    this.sse = sse;
    if (this.sse) {
      clients = new HashSet<>();
    } else {
      clients = null;
    }
    update();

    final String webpackBuildInfo = System.getProperty("webpack.build.info");

    if (webpackBuildInfo != null) {
      Thread watcher = new Thread(() -> {
        try (final WatchService watchService = FileSystems.getDefault().newWatchService()) {
          // this represents the full file
          final Path path = FileSystems.getDefault().getPath(webpackBuildInfo);
          final Path filename = path.getFileName();
          // we register to the parent only
          path.getParent().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

          while (true) {
            final WatchKey wk = watchService.take();
            for (WatchEvent<?> event : wk.pollEvents()) {
              //we only register "ENTRY_MODIFY" so the context is always a Path.
              final Path changed = (Path) event.context();
              if (changed.getFileName().equals(filename)) {
                update();
              }
            }
            // reset the key
            boolean valid = wk.reset();
            if (!valid) {
              log.info("Key has been unregistered");
              break;
            }
            Thread.sleep(300);
          }
        } catch (IOException | InterruptedException e) {
          log.error("webpack watch info failed.", e);
        }
      });
      watcher.setDaemon(true);
      watcher.start();
    }
  }

  private void update() {
    this.payload.set("{\"uuid\": \"" + UUID.randomUUID().toString() + "\"}");

    if (sse) {
      for (HttpServerResponse client : clients) {
        if (!client.ended()) {
          client.write(
            "event: reload\n" +
            "data: " + payload.get() + "\n\n");
        }
      }
    }
  }

  private HttpServerResponse addClient(HttpServerResponse client) {
    clients.add(client);
    client.exceptionHandler(t -> clients.remove(client));
    client.endHandler(v -> clients.remove(client));

    return client;
  }

  @Override
  public void handle(RoutingContext ctx) {
    if (ctx.request().method() == HttpMethod.GET) {
      // the check end point
      if ("/hot-reload".equals(ctx.request().path())) {
        if (sse) {
          addClient(ctx.response())
            .putHeader("Content-Type", "text/event-stream")
            .putHeader("Cache-Control", "no-cache")
            .putHeader("Access-Control-Allow-Origin", "*")
            .setChunked(true)
            .write(
              "event: reload\n" +
                "data: " + payload.get() + "\n\n");
        } else {
          ctx.response()
            .putHeader("Content-Type", "application/json")
            .putHeader("Cache-Control", "no-cache")
            .end(payload.get());
        }
        return;
      }

      // the script end point
      if ("/hot-reload/script".equals(ctx.request().path())) {
        ctx.response()
          .putHeader("Content-Type", "application/javascript")
          .end(sse ? SSE_SCRIPT : SCRIPT);
        return;
      }
    }
    // nothing to see here, continue...
    ctx.next();
  }
}
