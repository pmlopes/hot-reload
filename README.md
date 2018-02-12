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

A static handler will be returned using this factory. If there is a
file named `.hot-reload` on the current working directory then the static
handler is configured to not cache both the filesystem and the http responses.

## How to configure?

In your application router you should add the following code:

```java
final Router router = Router.router(vertx);

// development hot reload
router.get().handler(HotReload.create());
...
// Serve the static resources
router.route().handler(StaticHandler.create());
```

When there isn't a file named `.hot-reload` in the current working directory the handler will
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

When you start your application you should have the control file in the current working directory e.g.:

```bash
touch .hot-reload
java -jar target/fatjar.jar
```

Navigate to your HTML page where you loaded the script, and after that update the contents
of the reload control file e.g.:

```bash
echo "update!" >> .hot-reload
```

You will notice that a reload is triggered on the browser.
