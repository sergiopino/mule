/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.deployment.impl.internal.domain;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;
import static org.mule.runtime.api.util.Preconditions.checkArgument;
import static org.mule.runtime.container.api.MuleFoldersUtil.getDomainFolder;
import static org.mule.runtime.deployment.model.api.domain.Domain.DEFAULT_DOMAIN_NAME;
import static org.mule.runtime.deployment.model.internal.AbstractArtifactClassLoaderBuilder.PLUGIN_CLASSLOADER_IDENTIFIER;
import static org.mule.runtime.deployment.model.internal.AbstractArtifactClassLoaderBuilder.getArtifactPluginId;
import static org.mule.runtime.module.reboot.api.MuleContainerBootstrapUtils.getMuleDomainsDir;
import org.mule.runtime.deployment.model.api.domain.Domain;
import org.mule.runtime.deployment.model.api.domain.DomainDescriptor;
import org.mule.runtime.deployment.model.api.plugin.ArtifactPlugin;
import org.mule.runtime.deployment.model.api.plugin.ArtifactPluginDescriptor;
import org.mule.runtime.deployment.model.api.plugin.ArtifactPluginRepository;
import org.mule.runtime.deployment.model.internal.domain.DomainClassLoaderBuilder;
import org.mule.runtime.deployment.model.internal.plugin.PluginDependenciesResolver;
import org.mule.runtime.module.artifact.classloader.ArtifactClassLoader;
import org.mule.runtime.module.artifact.classloader.ClassLoaderRepository;
import org.mule.runtime.module.artifact.classloader.DeployableArtifactClassLoaderFactory;
import org.mule.runtime.module.artifact.classloader.MuleDeployableArtifactClassLoader;
import org.mule.runtime.module.artifact.descriptor.BundleDependency;
import org.mule.runtime.module.deployment.impl.internal.artifact.ArtifactFactory;
import org.mule.runtime.module.deployment.impl.internal.artifact.MuleContextListenerFactory;
import org.mule.runtime.module.deployment.impl.internal.plugin.ArtifactPluginDescriptorLoader;
import org.mule.runtime.module.deployment.impl.internal.plugin.DefaultArtifactPlugin;
import org.mule.runtime.module.service.ServiceRepository;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultDomainFactory implements ArtifactFactory<Domain> {

  private final DeployableArtifactClassLoaderFactory<DomainDescriptor> domainClassLoaderFactory;
  private final DomainManager domainManager;
  private final DomainDescriptorFactory domainDescriptorFactory;
  private final ClassLoaderRepository classLoaderRepository;
  private final ServiceRepository serviceRepository;
  private final ArtifactPluginRepository artifactPluginRepository;
  private final ArtifactPluginDescriptorLoader pluginDescriptorLoader;
  private final PluginDependenciesResolver pluginDependenciesResolver;
  private final DomainClassLoaderBuilderFactory domainClassLoaderBuilderFactory;

  private final ArtifactClassLoader containerClassLoader;
  private MuleContextListenerFactory muleContextListenerFactory;

  // TODO(pablo.kraan): domains - add javadoc
  public DefaultDomainFactory(DeployableArtifactClassLoaderFactory<DomainDescriptor> domainClassLoaderFactory,
                              DomainDescriptorFactory domainDescriptorFactory,
                              DomainManager domainManager, ArtifactClassLoader containerClassLoader,
                              ClassLoaderRepository classLoaderRepository, ServiceRepository serviceRepository,
                              ArtifactPluginDescriptorLoader pluginDescriptorLoader,
                              ArtifactPluginRepository artifactPluginRepository,
                              PluginDependenciesResolver pluginDependenciesResolver,
                              DomainClassLoaderBuilderFactory domainClassLoaderBuilderFactory) {
    checkArgument(domainDescriptorFactory != null, "domainDescriptorFactory cannot be null");
    checkArgument(domainManager != null, "Domain manager cannot be null");
    checkArgument(containerClassLoader != null, "Container classLoader cannot be null");
    checkArgument(serviceRepository != null, "Service repository cannot be null");
    checkArgument(pluginDescriptorLoader != null, "pluginDescriptorLoader cannot be null");
    checkArgument(artifactPluginRepository != null, "Artifact plugin repository cannot be null");
    checkArgument(pluginDependenciesResolver != null, "pluginDependenciesResolver cannot be null");
    checkArgument(domainClassLoaderBuilderFactory != null, "domainClassLoaderBuilderFactory cannot be null");

    this.classLoaderRepository = classLoaderRepository;
    this.containerClassLoader = containerClassLoader;
    this.domainDescriptorFactory = domainDescriptorFactory;
    this.domainClassLoaderFactory = domainClassLoaderFactory;
    this.domainManager = domainManager;
    this.serviceRepository = serviceRepository;
    this.pluginDescriptorLoader = pluginDescriptorLoader;
    this.artifactPluginRepository = artifactPluginRepository;
    this.pluginDependenciesResolver = pluginDependenciesResolver;
    this.domainClassLoaderBuilderFactory = domainClassLoaderBuilderFactory;
  }

  public void setMuleContextListenerFactory(MuleContextListenerFactory muleContextListenerFactory) {
    this.muleContextListenerFactory = muleContextListenerFactory;
  }

  @Override
  public Domain createArtifact(File domainLocation) throws IOException {
    String domainName = domainLocation.getName();
    Domain domain = domainManager.getDomain(domainName);
    if (domain != null) {
      throw new IllegalArgumentException(format("Domain '%s'  already exists", domainName));
    }
    if (domainName.contains(" ")) {
      throw new IllegalArgumentException("Mule domain name may not contain spaces: " + domainName);
    }

    return createDomainFrom(findDomain(domainName));
  }

  private Domain createDomainFrom(DomainDescriptor descriptor) throws IOException {
    // TODO(pablo.kraan): domains - remove duplicated code from app
    Set<ArtifactPluginDescriptor> pluginDescriptors = createArtifactPluginDescriptors(descriptor);

    List<ArtifactPluginDescriptor> applicationPluginDescriptors =
        concat(artifactPluginRepository.getContainerArtifactPluginDescriptors().stream()
            .filter(containerPluginDescriptor -> !pluginDescriptors.stream()
                .filter(appPluginDescriptor -> appPluginDescriptor.getName().equals(containerPluginDescriptor.getName()))
                .findAny().isPresent()),
               pluginDescriptors.stream())
                   .collect(Collectors.toList());

    List<ArtifactPluginDescriptor> resolvedArtifactPluginDescriptors =
        pluginDependenciesResolver.resolve(applicationPluginDescriptors);

    DomainClassLoaderBuilder artifactClassLoaderBuilder =
        domainClassLoaderBuilderFactory.createArtifactClassLoaderBuilder();
    MuleDeployableArtifactClassLoader domainClassLoader =
        artifactClassLoaderBuilder
            .addArtifactPluginDescriptors(resolvedArtifactPluginDescriptors.toArray(new ArtifactPluginDescriptor[0]))
            .setArtifactId(descriptor.getName()).setArtifactDescriptor(descriptor).build();


    List<ArtifactPlugin> artifactPlugins =
        createArtifactPluginList(domainClassLoader, resolvedArtifactPluginDescriptors);

    DefaultMuleDomain defaultMuleDomain =
        new DefaultMuleDomain(descriptor, domainClassLoader, classLoaderRepository, serviceRepository, artifactPlugins);

    if (muleContextListenerFactory != null) {
      defaultMuleDomain.setMuleContextListener(muleContextListenerFactory.create(descriptor.getName()));
    }
    DomainWrapper domainWrapper = new DomainWrapper(defaultMuleDomain, this);
    domainManager.addDomain(domainWrapper);
    return domainWrapper;
  }

  private DomainDescriptor findDomain(String domainName) throws IOException {
    if (DEFAULT_DOMAIN_NAME.equals(domainName)) {
      return new EmptyDomainDescriptor(new File(getMuleDomainsDir(), DEFAULT_DOMAIN_NAME));
    }

    File domainFolder = getDomainFolder(domainName);
    DomainDescriptor descriptor = domainDescriptorFactory.create(domainFolder);

    return descriptor;
  }

  private Set<ArtifactPluginDescriptor> createArtifactPluginDescriptors(DomainDescriptor descriptor) throws IOException {
    Set<ArtifactPluginDescriptor> pluginDescriptors = new HashSet<>();
    for (BundleDependency bundleDependency : descriptor.getClassLoaderModel().getDependencies()) {
      if (bundleDependency.getDescriptor().isPlugin()) {
        File pluginZip = new File(bundleDependency.getBundleUri());
        pluginDescriptors.add(pluginDescriptorLoader.load(pluginZip));
      }
    }
    return pluginDescriptors;
  }

  private List<ArtifactPlugin> createArtifactPluginList(MuleDeployableArtifactClassLoader domainClassLoader,
                                                        List<ArtifactPluginDescriptor> plugins) {
    return plugins.stream()
        .map(artifactPluginDescriptor -> new DefaultArtifactPlugin(getArtifactPluginId(domainClassLoader.getArtifactId(),
                                                                                       artifactPluginDescriptor.getName()),
                                                                   artifactPluginDescriptor, domainClassLoader
                                                                       .getArtifactPluginClassLoaders().stream()
                                                                       .filter(artifactClassLoader -> {
                                                                         final String artifactPluginDescriptorName =
                                                                             PLUGIN_CLASSLOADER_IDENTIFIER
                                                                                 + artifactPluginDescriptor.getName();
                                                                         return artifactClassLoader
                                                                             .getArtifactId()
                                                                             .endsWith(artifactPluginDescriptorName);
                                                                       })
                                                                       .findFirst().get()))
        .collect(toList());
  }

  @Override
  public File getArtifactDir() {
    return getMuleDomainsDir();
  }

  public void dispose(DomainWrapper domain) {
    domainManager.removeDomain(domain.getArtifactName());
  }

  public void start(DomainWrapper domainWrapper) {
    domainManager.addDomain(domainWrapper);
  }
}
