/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package xyz.jetdrone.vertx.hot.reload;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.UUID;

/**
 * A Simple Hot Reload for Eclipse Vert.x Web.
 * <p>
 * Usage: In your main HTML file add the following script at the end of the body.
 *
 * <code>
 *     &lt;script src="/hot-reload/script" type="application/javascript"&gt;&lt;/script&gt;
 * </code>
 *
 * Once the server stops or reload the handler the script will force a page reload.
 * During the down time of the server the script will attempt to reconnect every second.
 *
 * @author <a href="mailto:pmlopes@gmail.com">Paulo Lopes</a>
 */
@VertxGen
public interface HotReload extends Handler<RoutingContext> {

  // language=JavaScript
  String SCRIPT =
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
  String SSE_SCRIPT =
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

  /**
   * Creates a HotReload Handler that will setup 2 endpoints:
   * <p>
   * <ol>
   * <li><pre>/hot-reload</pre> the watch endpoint either a SSE or AJAX endpoint that returns the current deployment id</li>
   * <li><pre>/hot-reload/script</pre> the script to be added to the main HTML</li>
   * </ol>
   * <p>
   * By default it will use the SSE mode.
   *
   * @return HotReload Handler
   */
  static HotReload create() {
    return create(true);
  }

  /**
   * Creates a HotReload Handler that will setup 2 endpoints.
   *
   * @param sse if true then SSE will be used, otherwise AJAX.
   * @return HotReload Handler
   * @see #create()
   */
  static HotReload create(boolean sse) {
    final String payload = new JsonObject().put("uuid", UUID.randomUUID().toString()).encode();
    return ctx -> {
      if (ctx.request().method() == HttpMethod.GET) {
        // the check end point
        if ("/hot-reload".equals(ctx.request().path())) {
          if (sse) {
            ctx.response()
              .putHeader("Content-Type", "text/event-stream")
              .putHeader("Cache-Control", "no-cache")
              .putHeader("Access-Control-Allow-Origin", "*")
              .setChunked(true)
              .write(
                "event: reload\n" +
                  "data: " + payload + "\n\n");
          } else {
            ctx.response()
              .putHeader("Content-Type", "application/json")
              .putHeader("Cache-Control", "no-cache")
              .end(payload);
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
    };
  }
}
