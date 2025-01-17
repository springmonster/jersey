/*
 * Copyright (c) 2012, 2023 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.glassfish.jersey.client;

import java.security.AccessController;
import java.security.KeyStore;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Configuration;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.glassfish.jersey.SslConfigurator;
import org.glassfish.jersey.client.innate.inject.NonInjectionManager;
import org.glassfish.jersey.client.internal.LocalizationMessages;
import org.glassfish.jersey.client.spi.ClientBuilderListener;
import org.glassfish.jersey.client.spi.ConnectorProvider;
import org.glassfish.jersey.internal.ServiceFinder;
import org.glassfish.jersey.internal.config.ExternalPropertiesConfigurationFactory;
import org.glassfish.jersey.internal.util.ReflectionHelper;
import org.glassfish.jersey.internal.util.collection.UnsafeValue;
import org.glassfish.jersey.internal.util.collection.Values;
import org.glassfish.jersey.model.internal.RankedComparator;
import org.glassfish.jersey.model.internal.RankedProvider;

/**
 * Jersey provider of {@link javax.ws.rs.client.ClientBuilder JAX-RS client builder}.
 *
 * @author Marek Potociar
 */
public class JerseyClientBuilder extends ClientBuilder {

    private final ClientConfig config;
    private HostnameVerifier hostnameVerifier;
    private SslConfigurator sslConfigurator;
    private SSLContext sslContext;

    private static final List<ClientBuilderListener> CLIENT_BUILDER_LISTENERS;

    static {
        final List<RankedProvider<ClientBuilderListener>> listeners = new LinkedList<>();
        for (ClientBuilderListener listener : ServiceFinder.find(ClientBuilderListener.class)) {
            listeners.add(new RankedProvider<>(listener));
        }
        listeners.sort(new RankedComparator<>(RankedComparator.Order.ASCENDING));

        final List<ClientBuilderListener> sortedList = new LinkedList<>();
        for (RankedProvider<ClientBuilderListener> listener : listeners) {
            sortedList.add(listener.getProvider());
        }

        CLIENT_BUILDER_LISTENERS = Collections.unmodifiableList(sortedList);
    }

    /**
     * Create a new custom-configured {@link JerseyClient} instance.
     *
     * @return new configured Jersey client instance.
     * @since 2.5
     */
    public static JerseyClient createClient() {
        return new JerseyClientBuilder().build();
    }

    /**
     * Create a new custom-configured {@link JerseyClient} instance.
     *
     * @param configuration data used to provide initial configuration for the new
     *                      Jersey client instance.
     * @return new configured Jersey client instance.
     * @since 2.5
     */
    public static JerseyClient createClient(Configuration configuration) {
        return new JerseyClientBuilder().withConfig(configuration).build();
    }

    /**
     * Create new Jersey client builder instance.
     */
    public JerseyClientBuilder() {
        this.config = new ClientConfig();

        init(this);
    }

    private static void init(ClientBuilder builder) {
        for (ClientBuilderListener listener : CLIENT_BUILDER_LISTENERS) {
            listener.onNewBuilder(builder);
        }
    }

    @Override
    public JerseyClientBuilder sslContext(SSLContext sslContext) {
        if (sslContext == null) {
            throw new NullPointerException(LocalizationMessages.NULL_SSL_CONTEXT());
        }
        this.sslContext = sslContext;
        sslConfigurator = null;
        return this;
    }

    @Override
    public JerseyClientBuilder keyStore(KeyStore keyStore, char[] password) {
        if (keyStore == null) {
            throw new NullPointerException(LocalizationMessages.NULL_KEYSTORE());
        }
        if (password == null) {
            throw new NullPointerException(LocalizationMessages.NULL_KEYSTORE_PASWORD());
        }
        if (sslConfigurator == null) {
            sslConfigurator = SslConfigurator.newInstance();
        }
        sslConfigurator.keyStore(keyStore);
        sslConfigurator.keyPassword(password);
        sslContext = null;
        return this;
    }

    @Override
    public JerseyClientBuilder trustStore(KeyStore trustStore) {
        if (trustStore == null) {
            throw new NullPointerException(LocalizationMessages.NULL_TRUSTSTORE());
        }
        if (sslConfigurator == null) {
            sslConfigurator = SslConfigurator.newInstance();
        }
        sslConfigurator.trustStore(trustStore);
        sslContext = null;
        return this;
    }

    @Override
    public JerseyClientBuilder hostnameVerifier(HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
        return this;
    }

    @Override
    public ClientBuilder executorService(ExecutorService executorService) {
        config.executorService(executorService);
        return this;
    }

    @Override
    public ClientBuilder scheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
        config.scheduledExecutorService(scheduledExecutorService);
        return this;
    }

    @Override
    public ClientBuilder connectTimeout(long timeout, TimeUnit unit) {
        if (timeout < 0) {
            throw new IllegalArgumentException("Negative timeout.");
        }

        this.property(ClientProperties.CONNECT_TIMEOUT, Math.toIntExact(unit.toMillis(timeout)));
        return this;
    }

    @Override
    public ClientBuilder readTimeout(long timeout, TimeUnit unit) {
        if (timeout < 0) {
            throw new IllegalArgumentException("Negative timeout.");
        }

        this.property(ClientProperties.READ_TIMEOUT, Math.toIntExact(unit.toMillis(timeout)));
        return this;
    }

    @Override
    public JerseyClient build() {
        ExternalPropertiesConfigurationFactory.configure(this.config);
        setConnectorFromProperties();

        if (sslContext != null) {
            return new JerseyClient(config, sslContext, hostnameVerifier, null);
        } else if (sslConfigurator != null) {
            final SslConfigurator sslConfiguratorCopy = sslConfigurator.copy();
            return new JerseyClient(
                    config,
                    Values.lazy(new UnsafeValue<SSLContext, IllegalStateException>() {
                        @Override
                        public SSLContext get() {
                            return sslConfiguratorCopy.createSSLContext();
                        }
                    }),
                    hostnameVerifier);
        } else {
            return new JerseyClient(config, (UnsafeValue<SSLContext, IllegalStateException>) null, hostnameVerifier);
        }
    }

    private void setConnectorFromProperties() {
        final Object connectorClass = config.getProperty(ClientProperties.CONNECTOR_PROVIDER);
        if (connectorClass != null) {
            if (String.class.isInstance(connectorClass)) {
                Class<? extends ConnectorProvider> clazz
                        = AccessController.doPrivileged(ReflectionHelper.classForNamePA((String) connectorClass));
                final ConnectorProvider connectorProvider = new NonInjectionManager().justCreate(clazz);
                config.connectorProvider(connectorProvider);
            } else {
                throw new IllegalArgumentException();
            }
        }
    }

    @Override
    public ClientConfig getConfiguration() {
        return config;
    }

    @Override
    public JerseyClientBuilder property(String name, Object value) {
        this.config.property(name, value);
        return this;
    }

    @Override
    public JerseyClientBuilder register(Class<?> componentClass) {
        this.config.register(componentClass);
        return this;
    }

    @Override
    public JerseyClientBuilder register(Class<?> componentClass, int priority) {
        this.config.register(componentClass, priority);
        return this;
    }

    @Override
    public JerseyClientBuilder register(Class<?> componentClass, Class<?>... contracts) {
        this.config.register(componentClass, contracts);
        return this;
    }

    @Override
    public JerseyClientBuilder register(Class<?> componentClass, Map<Class<?>, Integer> contracts) {
        this.config.register(componentClass, contracts);
        return this;
    }

    @Override
    public JerseyClientBuilder register(Object component) {
        this.config.register(component);
        return this;
    }

    @Override
    public JerseyClientBuilder register(Object component, int priority) {
        this.config.register(component, priority);
        return this;
    }

    @Override
    public JerseyClientBuilder register(Object component, Class<?>... contracts) {
        this.config.register(component, contracts);
        return this;
    }

    @Override
    public JerseyClientBuilder register(Object component, Map<Class<?>, Integer> contracts) {
        this.config.register(component, contracts);
        return this;
    }

    @Override
    public JerseyClientBuilder withConfig(Configuration config) {
        this.config.loadFrom(config);
        return this;
    }
}
