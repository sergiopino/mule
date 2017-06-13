/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.deployment.model.api.domain;

import static org.mule.runtime.container.api.MuleFoldersUtil.getAppConfigFolderPath;
import org.mule.runtime.deployment.model.api.DeployableArtifactDescriptor;
import org.mule.runtime.deployment.model.api.plugin.ArtifactPluginDescriptor;

import com.google.common.collect.ImmutableList;

import java.io.File;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents the description of a domain.
 */
public class DomainDescriptor extends DeployableArtifactDescriptor {

  // TODO(pablo.kraan): domains - remove constant duplication (is on Domain)
  public static final String DEFAULT_CONFIGURATION_RESOURCE = "mule-domain-config.xml";
  public static final String DEFAULT_CONFIGURATION_RESOURCE_LOCATION = Paths.get("mule", "mule-config.xml").toString();
  public static final String MULE_DOMAIN_JSON_LOCATION = Paths.get("META-INF", "mule-artifact", "mule-domain.json").toString();


  private List<String> configResources =
    ImmutableList.<String>builder().add(getAppConfigFolderPath() + DEFAULT_CONFIGURATION_RESOURCE).build();
  private String[] absoluteResourcePaths;
  private File[] configResourcesFile;
  private Set<ArtifactPluginDescriptor> plugins = new HashSet<>(0);

  /**
   * Creates a new domain descriptor
   *
   * @param name domain name. Non empty.
   */
  public DomainDescriptor(String name) {
    super(name);
  }

  // TODO(pablo.kraan): domains - check if these two methods cam be moved up
  public List<String> getConfigResources() {
    return configResources;
  }

  public void setConfigResources(List<String> configResources) {
    this.configResources = configResources;
  }

  // TODO(pablo.kraan): domains - check if these two methods cam be moved up
  public void setConfigResourcesFile(File[] configResourcesFile) {
    this.configResourcesFile = configResourcesFile;
  }

  public File[] getConfigResourcesFile() {
    return configResourcesFile;
  }

  // TODO(pablo.kraan): domains - check if these two methods cam be moved up
  public String[] getAbsoluteResourcePaths() {
    return absoluteResourcePaths;
  }

  public void setAbsoluteResourcePaths(String[] absoluteResourcePaths) {
    this.absoluteResourcePaths = absoluteResourcePaths;
  }


  // TODO(pablo.kraan): domains - check if these two methods cam be moved up
  /**
   * @return the {@code ApplicationPluginDescriptor} that describe the plugins the application requires.
   */
  public Set<ArtifactPluginDescriptor> getPlugins() {
    return plugins;
  }

  /**
   * @param plugins a set of {@code ApplicationPluginDescriptor} which are dependencies of the application.
   */
  public void setPlugins(Set<ArtifactPluginDescriptor> plugins) {
    this.plugins = plugins;
  }
}
