package xyz.jetdrone.vertx.hot.reload;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.UUID;

@VertxGen
public interface HotReload extends Handler<RoutingContext> {

    String SCRIPT =
            "(function () {\n" +
            "    var lastUpdate = -1;\n" +
            "    var forceReload = false;\n" +
            "\n" +
            "    var load = function () {\n" +
            "      var xobj = new XMLHttpRequest();\n" +
            "      xobj.overrideMimeType(\"application/json\");\n" +
            "      xobj.open('GET', \"/hot\", true);\n" +
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

    static HotReload create() {
        final String payload = new JsonObject().put("uuid", UUID.randomUUID().toString()).encode();
        return ctx -> {
            if (ctx.request().method() == HttpMethod.GET) {
                // the check end point
                if ("/hot".equals(ctx.request().path())) {
                    ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end(payload);
                    return;
                }

                // the script end point
                if ("/hot/script".equals(ctx.request().path())) {
                    ctx.response()
                            .putHeader("Content-Type", "application/javascript")
                            .end(SCRIPT);
                    return;
                }
            }
            // nothing to see here, continue...
            ctx.next();
        };
    }
}
