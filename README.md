# hot-reload

This is a experimental vertx-web handler for hot reload. This handler is used
in the webpack-vertx-plugin but it is not necessary to, you can also use it
independently.

## What's inside?

This module provides 2 handlers in one:

* A `hot-reload` script + backend based either in SSE or pooling
* A static file server (delegates to the official static file server)

### hot-reload script

The script can listen for updates either as a SSE stream or using pooling.
It defaults to SSE as it is supported on modern web browsers but for legacy
it can be configured to perform HTTP pooling.

### static handler

A static handler will be returned using this factory. If the environment variable
`VERTX_HOT_RELOAD` is set then the static handler is wrapped to serve resources
from:

```java
System.getProperty("user.dir") + "/src/main/resources/" + DEFAULT_WEB_ROOT
```

And caching of resources is disabled.

## How to configure?

In your application router you should add the following code:

```java
final Router router = Router.router(vertx);

// development hot reload
router.get().handler(HotReload.create());
...
// Serve the static resources
router.route().handler(HotReload.createStaticHandler());
```

When the environment variable `VERTX_HOT_RELOAD` is not defined the first handler will
be a NO-OP handler and the last will return a unmodified `StaticHandler`.

In your html application you should have:

```html
<!DOCTYPE html>
<html>
<body>
  <script src="/hot-reload/script"></script>
</body>
</html>
```

## How it works?

When you start your application you should pass the required environment variable in order
to trigger the handler to operate in hot reload mode e.g.:

```bash
VERTX_HOT_RELOAD="/tmp/watch-me.tmp" java -jar target/fatjar.jar
```

Navigate to your HTML page where you loaded the script, and after that update the contents
of the reload control file e.g.:

```bash
echo "update!" > "/tmp/watch-me.tmp"
```

You will notice that a reload is triggered on the browser.
