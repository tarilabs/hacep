/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.redhat.hacep.configuration.impl;

import it.redhat.hacep.camel.Putter;
import it.redhat.hacep.configuration.DroolsConfiguration;
import it.redhat.hacep.configuration.JmsConfiguration;
import it.redhat.hacep.configuration.RouterManager;
import it.redhat.hacep.configuration.annotations.HACEPCamelContext;
import it.redhat.hacep.configuration.annotations.HACEPFactCache;
import it.redhat.hacep.model.Fact;
import it.redhat.hacep.model.Key;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.infinispan.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

public class CamelRouterManager implements RouterManager {

    private final static Logger log = LoggerFactory.getLogger(CamelRouterManager.class);

    public static final String CAMEL_ROUTE = "facts";

    @Inject
    private JmsConfiguration jmsConfiguration;

    @Inject
    private DroolsConfiguration droolsConfiguration;

    @Inject
    @HACEPFactCache
    private Cache<Key, Fact> factCache;

    private CamelContext camelContext;

    public CamelRouterManager() {
    }

    @Override
    public void start() {
        try {
            camelContext.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {
        try {
            camelContext.stop();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void suspend() {
        log.info("Suspending route " + CamelRouterManager.CAMEL_ROUTE);
        try {
            camelContext.suspendRoute(CAMEL_ROUTE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void resume() {
        log.info("Resuming route " + CamelRouterManager.CAMEL_ROUTE);
        try {
            camelContext.resumeRoute(CAMEL_ROUTE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @PostConstruct
    public void createCamelContext() {
        camelContext = new DefaultCamelContext();
        try {

            JmsComponent component = JmsComponent.jmsComponent(jmsConfiguration.getConnectionFactory());
            camelContext.addComponent("jms", component);
            camelContext.addRoutes(new RouteBuilder() {

                @Override
                public void configure() throws Exception {
                    String uri = "jms:" + jmsConfiguration.getQueueName()
                            + "?concurrentConsumers=" + jmsConfiguration.getMaxConsumers()
                            + "&maxConcurrentConsumers=" + jmsConfiguration.getMaxConsumers();

                    from(uri)
                            .routeId(CAMEL_ROUTE)
                            .to("direct:putInGrid");
                }
            });

            camelContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:putInGrid")
                            .bean(new Putter(droolsConfiguration.getKeyBuilder(), factCache), "put(${body})");
                }
            });
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    @Produces
    @ApplicationScoped
    @HACEPCamelContext
    public CamelContext getCamelContext() {
        return camelContext;
    }
}
