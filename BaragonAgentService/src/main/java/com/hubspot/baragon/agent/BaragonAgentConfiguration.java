package com.hubspot.baragon.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hubspot.baragon.config.ZooKeeperConfiguration;
import com.hubspot.baragon.config.LoadBalancerConfiguration;
import io.dropwizard.Configuration;

public class BaragonAgentConfiguration extends Configuration {
  @JsonProperty("zookeeper")
  private ZooKeeperConfiguration zooKeeperConfiguration;

  @JsonProperty("loadBalancer")
  private LoadBalancerConfiguration loadBalancerConfiguration;

  public ZooKeeperConfiguration getZooKeeperConfiguration() {
    return zooKeeperConfiguration;
  }

  public void setZooKeeperConfiguration(ZooKeeperConfiguration zooKeeperConfiguration) {
    this.zooKeeperConfiguration = zooKeeperConfiguration;
  }

  public LoadBalancerConfiguration getLoadBalancerConfiguration() {
    return loadBalancerConfiguration;
  }

  public void setLoadBalancerConfiguration(LoadBalancerConfiguration loadBalancerConfiguration) {
    this.loadBalancerConfiguration = loadBalancerConfiguration;
  }
}
