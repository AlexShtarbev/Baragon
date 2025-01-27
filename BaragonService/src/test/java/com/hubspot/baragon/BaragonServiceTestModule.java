package com.hubspot.baragon;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.hubspot.baragon.config.HttpClientConfiguration;
import com.hubspot.baragon.data.BaragonLoadBalancerDatastore;
import com.hubspot.baragon.service.BaragonLoadBalancerTestDatastore;
import com.hubspot.baragon.service.BaragonServiceModule;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

public class BaragonServiceTestModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(TestingServer.class).in(Scopes.SINGLETON);
    bind(BaragonLoadBalancerDatastore.class).to(BaragonLoadBalancerTestDatastore.class).in(Scopes.SINGLETON);

    final ObjectMapper objectMapper = new ObjectMapper();

    objectMapper.registerModule(new GuavaModule());
    objectMapper.registerModule(new Jdk8Module());

    bind(ObjectMapper.class).toInstance(objectMapper);

    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    Logger rootLogger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    rootLogger.setLevel(Level.ERROR);
  }

  @Singleton
  @Provides
  public CuratorFramework provideCurator(TestingServer testingServer) throws InterruptedException {
    final CuratorFramework client = CuratorFrameworkFactory.newClient(testingServer.getConnectString(), new RetryOneTime(1));
    client.start();
    return client;
  }

  @Singleton
  @Provides
  @Named(BaragonDataModule.BARAGON_SERVICE_WORKER_LAST_START)
  public AtomicLong providesLastStart() {
    return new AtomicLong();
  }

  @Provides
  @Singleton
  @Named(BaragonServiceModule.BARAGON_SERVICE_HTTP_CLIENT)
  public AsyncHttpClient providesHttpClient(HttpClientConfiguration config) {
    AsyncHttpClientConfig.Builder builder = new AsyncHttpClientConfig.Builder();

    builder.setMaxRequestRetry(config.getMaxRequestRetry());
    builder.setRequestTimeout(config.getRequestTimeoutInMs());
    builder.setFollowRedirect(true);
    builder.setConnectTimeout(config.getConnectionTimeoutInMs());
    builder.setUserAgent(config.getUserAgent());

    return new AsyncHttpClient(builder.build());
  }
}
