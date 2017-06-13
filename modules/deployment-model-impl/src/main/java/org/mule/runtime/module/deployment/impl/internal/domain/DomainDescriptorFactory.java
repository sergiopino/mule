/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.deployment.impl.internal.domain;

import static java.io.File.separator;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.mule.runtime.api.util.Preconditions.checkArgument;
import static org.mule.runtime.core.config.bootstrap.ArtifactType.APP;
import static org.mule.runtime.core.config.bootstrap.ArtifactType.DOMAIN;
import static org.mule.runtime.deployment.model.api.domain.DomainDescriptor.DEFAULT_CONFIGURATION_RESOURCE;
import static org.mule.runtime.deployment.model.api.domain.DomainDescriptor.DEFAULT_CONFIGURATION_RESOURCE_LOCATION;
import static org.mule.runtime.deployment.model.api.plugin.ArtifactPluginDescriptor.MULE_ARTIFACT_FOLDER;
import org.mule.runtime.api.deployment.meta.MuleArtifactLoaderDescriptor;
import org.mule.runtime.api.deployment.meta.MuleDomainModel;
import org.mule.runtime.api.deployment.persistence.MuleDomainModelJsonSerializer;
import org.mule.runtime.api.meta.MuleVersion;
import org.mule.runtime.container.api.MuleFoldersUtil;
import org.mule.runtime.deployment.model.api.application.ApplicationDescriptor;
import org.mule.runtime.deployment.model.api.domain.DomainDescriptor;
import org.mule.runtime.deployment.model.api.plugin.ArtifactPluginDescriptor;
import org.mule.runtime.module.artifact.descriptor.ArtifactDescriptorCreateException;
import org.mule.runtime.module.artifact.descriptor.ArtifactDescriptorFactory;
import org.mule.runtime.module.artifact.descriptor.BundleDependency;
import org.mule.runtime.module.artifact.descriptor.BundleDescriptor;
import org.mule.runtime.module.artifact.descriptor.BundleDescriptorLoader;
import org.mule.runtime.module.artifact.descriptor.ClassLoaderModel;
import org.mule.runtime.module.artifact.descriptor.ClassLoaderModelLoader;
import org.mule.runtime.module.artifact.descriptor.InvalidDescriptorLoaderException;
import org.mule.runtime.module.deployment.impl.internal.artifact.DescriptorLoaderRepository;
import org.mule.runtime.module.deployment.impl.internal.artifact.LoaderNotFoundException;
import org.mule.runtime.module.deployment.impl.internal.plugin.ArtifactPluginDescriptorLoader;

import com.google.common.collect.ImmutableList;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;

/**
 * Creates artifact descriptor for application
 */
// TODO(pablo.kraan): domains - add unit tests
public class DomainDescriptorFactory implements ArtifactDescriptorFactory<DomainDescriptor> {

  private static final String MULE_CONFIG_FILES_FOLDER = "mule";
  private static final String MULE_DOMAIN_JSON = "mule-domain.json";

  private final ArtifactPluginDescriptorLoader artifactPluginDescriptorLoader;
  private final DescriptorLoaderRepository descriptorLoaderRepository;

  public DomainDescriptorFactory(ArtifactPluginDescriptorLoader artifactPluginDescriptorLoader,
                                      DescriptorLoaderRepository descriptorLoaderRepository) {
    checkArgument(artifactPluginDescriptorLoader != null, "ApplicationPluginDescriptorFactory cannot be null");

    this.artifactPluginDescriptorLoader = artifactPluginDescriptorLoader;
    this.descriptorLoaderRepository = descriptorLoaderRepository;
  }

  @Override
  public DomainDescriptor create(File artifactFolder) throws ArtifactDescriptorCreateException {
    DomainDescriptor domainDescriptor;
    // TODO(pablo.kraan): domain - change constnat
    final File mulePluginJsonFile = new File(artifactFolder, MULE_ARTIFACT_FOLDER + separator + MULE_DOMAIN_JSON);
    if (!mulePluginJsonFile.exists()) {
      throw new IllegalStateException("Artifact descriptor does not exists: " + mulePluginJsonFile);
    }
    domainDescriptor = loadFromJsonDescriptor(artifactFolder, mulePluginJsonFile);

    return domainDescriptor;
  }

  protected static String invalidClassLoaderModelIdError(File pluginFolder,
                                                         MuleArtifactLoaderDescriptor classLoaderModelLoaderDescriptor) {
    return format("The identifier '%s' for a class loader model descriptor is not supported (error found while reading plugin '%s')",
                  classLoaderModelLoaderDescriptor.getId(),
                  pluginFolder.getAbsolutePath());
  }

  // TODO(pablo.kraan): domains - try to remove duplication from this class and app's
  private BundleDescriptor getBundleDescriptor(File appFolder, MuleDomainModel muleDomainModel) {
    BundleDescriptorLoader bundleDescriptorLoader;
    try {
      bundleDescriptorLoader =
        descriptorLoaderRepository.get(muleDomainModel.getBundleDescriptorLoader().getId(), APP,
                                       BundleDescriptorLoader.class);
    } catch (LoaderNotFoundException e) {
      throw new ArtifactDescriptorCreateException(invalidBundleDescriptorLoaderIdError(appFolder, muleDomainModel
        .getBundleDescriptorLoader()));
    }

    try {
      return bundleDescriptorLoader.load(appFolder, muleDomainModel.getBundleDescriptorLoader().getAttributes(), APP);
    } catch (InvalidDescriptorLoaderException e) {
      throw new ArtifactDescriptorCreateException(e);
    }
  }

  protected static String invalidBundleDescriptorLoaderIdError(File pluginFolder,
                                                               MuleArtifactLoaderDescriptor bundleDescriptorLoader) {
    return format("The identifier '%s' for a bundle descriptor loader is not supported (error found while reading plugin '%s')",
                  bundleDescriptorLoader.getId(),
                  pluginFolder.getAbsolutePath());
  }

  private DomainDescriptor loadFromJsonDescriptor(File domainFolder, File muleApplicationJsonFile) {
    final MuleDomainModel muleDomainModel = getMuleDomainJsonDescriber(muleApplicationJsonFile);

    final DomainDescriptor descriptor = new DomainDescriptor(domainFolder.getName());
    descriptor.setArtifactLocation(domainFolder);
    descriptor.setRootFolder(domainFolder);
    descriptor.setBundleDescriptor(getBundleDescriptor(domainFolder, muleDomainModel));
    descriptor.setMinMuleVersion(new MuleVersion(muleDomainModel.getMinMuleVersion()));
    descriptor.setRedeploymentEnabled(muleDomainModel.isRedeploymentEnabled());

    List<String> configs = muleDomainModel.getConfigs();
    if (configs != null && !configs.isEmpty()) {
      descriptor.setConfigResources(configs.stream().map(configFile -> appendMuleFolder(configFile))
                                      .collect(toList()));
      List<File> configFiles = descriptor.getConfigResources()
        .stream()
        .map(config -> new File(domainFolder, config)).collect(toList());
      descriptor.setConfigResourcesFile(configFiles.toArray(new File[configFiles.size()]));
      descriptor.setAbsoluteResourcePaths(configFiles.stream().map(configFile -> configFile.getAbsolutePath()).collect(toList())
                                            .toArray(new String[configFiles.size()]));
    } else {
      File configFile = new File(domainFolder, appendMuleFolder(DEFAULT_CONFIGURATION_RESOURCE));
      descriptor.setConfigResourcesFile(new File[] {configFile});
      descriptor.setConfigResources(ImmutableList.<String>builder().add(DEFAULT_CONFIGURATION_RESOURCE_LOCATION).build());
      descriptor.setAbsoluteResourcePaths(new String[] {configFile.getAbsolutePath()});
    }

    muleDomainModel.getClassLoaderModelLoaderDescriptor().ifPresent(classLoaderModelLoaderDescriptor -> {
      ClassLoaderModel classLoaderModel = getClassLoaderModel(domainFolder, classLoaderModelLoaderDescriptor);
      descriptor.setClassLoaderModel(classLoaderModel);

      try {
        descriptor.setPlugins(createArtifactPluginDescriptors(classLoaderModel));
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    });
    // TODO(pablo.kraan): domains - do domain support configuration properties?
    //File domainClassesFolder = getDomainClassFolder(descriptor);
    // get a ref to an optional app props file (right next to the descriptor)
    //setDomainProperties(descriptor, new File(domainClassesFolder, DEFAULT_ARTIFACT_PROPERTIES_RESOURCE));
    return descriptor;
  }

  private String appendMuleFolder(String configFile) {
    return MULE_CONFIG_FILES_FOLDER + File.separator + configFile;
  }

  private MuleDomainModel getMuleDomainJsonDescriber(File jsonFile) {
    try (InputStream stream = new FileInputStream(jsonFile)) {
      return new MuleDomainModelJsonSerializer().deserialize(IOUtils.toString(stream));
    } catch (IOException e) {
      throw new IllegalArgumentException(format("Could not read extension describer on plugin '%s'", jsonFile.getAbsolutePath()),
                                         e);
    }
  }

  private ClassLoaderModel getClassLoaderModel(File domainFolder,
                                               MuleArtifactLoaderDescriptor classLoaderModelLoaderDescriptor) {
    ClassLoaderModelLoader classLoaderModelLoader;
    try {
      classLoaderModelLoader =
        descriptorLoaderRepository.get(classLoaderModelLoaderDescriptor.getId(), APP, ClassLoaderModelLoader.class);
    } catch (LoaderNotFoundException e) {
      throw new ArtifactDescriptorCreateException(invalidClassLoaderModelIdError(domainFolder,
                                                                                 classLoaderModelLoaderDescriptor));
    }

    final ClassLoaderModel classLoaderModel;
    try {
      classLoaderModel = classLoaderModelLoader.load(domainFolder, classLoaderModelLoaderDescriptor.getAttributes(), DOMAIN);
    } catch (InvalidDescriptorLoaderException e) {
      throw new ArtifactDescriptorCreateException(e);
    }
    return classLoaderModel;
  }

  private Set<ArtifactPluginDescriptor> createArtifactPluginDescriptors(ClassLoaderModel classLoaderModel)
    throws IOException {
    Set<ArtifactPluginDescriptor> pluginDescriptors = new HashSet<>();
    for (BundleDependency bundleDependency : classLoaderModel.getDependencies()) {
      if (bundleDependency.getDescriptor().isPlugin()) {
        File pluginFile = new File(bundleDependency.getBundleUri());
        pluginDescriptors.add(artifactPluginDescriptorLoader.load(pluginFile));
      }
    }
    return pluginDescriptors;
  }

  protected File getAppLibFolder(ApplicationDescriptor descriptor) {
    return MuleFoldersUtil.getAppLibFolder(descriptor.getName());
  }

  protected File getAppSharedLibsFolder(ApplicationDescriptor descriptor) {
    return MuleFoldersUtil.getAppSharedLibsFolder(descriptor.getName());
  }

  protected File getDomainClassFolder(DomainDescriptor descriptor) {
    return MuleFoldersUtil.getAppClassesFolder(descriptor.getName());
  }


}
