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
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import xyz.jetdrone.vertx.hot.reload.impl.HotReloadImpl;

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

  Logger LOGGER = LoggerFactory.getLogger(HotReload.class);

  /**
   * If the system property <code>hot.reload</code> is true then it creates a HotReload Handler that will setup 2 endpoints:
   * <p>
   * <ol>
   * <li><pre>/hot-reload</pre> the watch endpoint either a SSE or AJAX endpoint that returns the current deployment id</li>
   * <li><pre>/hot-reload/script</pre> the script to be added to the main HTML</li>
   * </ol>
   * <p>
   * By default it will use the SSE mode.
   *
   * Else this is a noop handler (pass through).
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
    return new HotReloadImpl(sse);
  }
}
