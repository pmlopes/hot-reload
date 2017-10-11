package xyz.jetdrone.vertx.hot.reload.impl;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
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
      "        if (xobj.readyState === XMLHttpRequest.DONE) {\n" +
      "          if (xobj.status !== 200) {\n" +
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
      "              /* recheck in a second */\n" +
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
      "       /* chrome keeps retrying while FF not, so we handle this manually */\n" +
      "       hotReloadSSE.close();\n" +
      "      /* recheck in a second */\n" +
      "      setTimeout(load, 1000);\n" +
      "    }\n" +
      "  };\n" +
      "  load();\n" +
      "})();\n";

  private final AtomicReference<String> payload = new AtomicReference<>();
  private final AtomicReference<Vertx> vertx = new AtomicReference<>();
  private final boolean sse;
  private final boolean active;
  private final Set<HttpServerResponse> clients;

  public HotReloadImpl(boolean sse) {
    this.sse = sse;
    this.active = System.getenv("VERTX_HOT_RELOAD") != null;

    if (this.sse && this.active) {
      clients = new HashSet<>();
    } else {
      clients = null;
    }

    if (this.active) {
      update();

      final String webpackBuildInfo = System.getenv("VERTX_HOT_RELOAD");

      if (webpackBuildInfo != null && webpackBuildInfo.length() > 0) {
        Thread watcher = new Thread(() -> {
          try (final WatchService watchService = FileSystems.getDefault().newWatchService()) {
            // this represents the full file
            final Path path = FileSystems.getDefault().getPath(webpackBuildInfo);
            // if the watched file does not exist warn about it!
            if (!path.toFile().exists()) {
              LOGGER.warn("Watched file (" + webpackBuildInfo + ") does not exist!");
            }
            final Path filename = path.getFileName();
            final Path parent = path.getParent();

            // if the watched parent does not exist we can't continue!
            if (!parent.toFile().exists()) {
              LOGGER.error("Watched dir (" + parent + ") does not exist watching is disabled.");
              return;
            }

            // we register to the parent only
            parent.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

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
                LOGGER.info("Key has been unregistered");
                break;
              }
              Thread.sleep(300);
            }
          } catch (IOException | InterruptedException | UnsupportedOperationException e) {
            LOGGER.error("webpack watch info failed.", e);
          }
        });
        watcher.setDaemon(true);
        watcher.start();
      }
    } else {
      LOGGER.info("Hot Reload is disabled!");
    }
  }

  private void update() {
    LOGGER.debug("updating clients");

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
    if (active && ctx.request().method() == HttpMethod.GET) {
      // the check end point
      if ("/hot-reload".equals(ctx.request().path())) {
        if (sse) {
          if (vertx.compareAndSet(null, ctx.vertx())) {
            vertx.get().setPeriodic(15_000L, p -> {
              for (HttpServerResponse client : clients) {
                if (!client.ended()) {
                  client.write(
                    "event: ping\n" +
                    "data: {\"ping\": \"" + this.hashCode() + "\"}\n\n");
                }
              }
            });
          }
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
