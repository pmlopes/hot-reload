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

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.File;

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
public interface StaticHandler extends io.vertx.ext.web.handler.StaticHandler {

  Logger LOGGER = LoggerFactory.getLogger(StaticHandler.class);

  static io.vertx.ext.web.handler.StaticHandler create() {
    final File control = new File(System.getProperty("user.dir"), ".hot-reload");

    if (control.exists()) {
      LOGGER.info("Serving static resources without cache");
      return io.vertx.ext.web.handler.StaticHandler.create()
        .setAllowRootFileSystemAccess(true)
        .setCachingEnabled(false);
    }

    return io.vertx.ext.web.handler.StaticHandler.create();
  }

  static io.vertx.ext.web.handler.StaticHandler create(String root) {
    final File control = new File(System.getProperty("user.dir"), ".hot-reload");

    if (control.exists()) {
      LOGGER.info("Serving static resources without cache");
      return io.vertx.ext.web.handler.StaticHandler.create(root)
        .setAllowRootFileSystemAccess(true)
        .setCachingEnabled(false);
    }

    return io.vertx.ext.web.handler.StaticHandler.create(root);
  }

  @GenIgnore
  static io.vertx.ext.web.handler.StaticHandler create(String root, ClassLoader classLoader) {
    final File control = new File(System.getProperty("user.dir"), ".hot-reload");

    if (control.exists()) {
      LOGGER.info("Serving static resources without cache");
      return io.vertx.ext.web.handler.StaticHandler.create(root, classLoader)
        .setAllowRootFileSystemAccess(true)
        .setCachingEnabled(false);
    }

    return io.vertx.ext.web.handler.StaticHandler.create(root, classLoader);
  }
}
