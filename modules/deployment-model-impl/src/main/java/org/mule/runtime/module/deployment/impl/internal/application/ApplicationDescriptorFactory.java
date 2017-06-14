/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.deployment.impl.internal.application;

import static java.io.File.separator;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.mule.runtime.api.util.Preconditions.checkArgument;
import static org.mule.runtime.core.config.bootstrap.ArtifactType.APP;
import static org.mule.runtime.deployment.model.api.DeployableArtifactDescriptor.DEFAULT_ARTIFACT_PROPERTIES_RESOURCE;
import static org.mule.runtime.deployment.model.api.application.ApplicationDescriptor.DEFAULT_CONFIGURATION_RESOURCE;
import static org.mule.runtime.deployment.model.api.application.ApplicationDescriptor.DEFAULT_CONFIGURATION_RESOURCE_LOCATION;
import static org.mule.runtime.deployment.model.api.application.ApplicationDescriptor.MULE_APPLICATION_JSON;
import static org.mule.runtime.deployment.model.api.plugin.ArtifactPluginDescriptor.MULE_ARTIFACT_FOLDER;
import org.mule.runtime.api.deployment.meta.MuleApplicationModel;
import org.mule.runtime.api.deployment.meta.MuleArtifactLoaderDescriptor;
import org.mule.runtime.api.deployment.persistence.MuleApplicationModelJsonSerializer;
import org.mule.runtime.api.meta.MuleVersion;
import org.mule.runtime.container.api.MuleFoldersUtil;
import org.mule.runtime.core.api.util.PropertiesUtils;
import org.mule.runtime.deployment.model.api.application.ApplicationDescriptor;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.io.IOUtils;

/**
 * Creates artifact descriptor for application
 */
public class ApplicationDescriptorFactory implements ArtifactDescriptorFactory<ApplicationDescriptor> {

  public static final String SYSTEM_PROPERTY_OVERRIDE = "-O";

  private static final String MULE_CONFIG_FILES_FOLDER = "mule";

  private final ArtifactPluginDescriptorLoader artifactPluginDescriptorLoader;
  private final DescriptorLoaderRepository descriptorLoaderRepository;

  public ApplicationDescriptorFactory(ArtifactPluginDescriptorLoader artifactPluginDescriptorLoader,
                                      DescriptorLoaderRepository descriptorLoaderRepository) {
    checkArgument(artifactPluginDescriptorLoader != null, "ApplicationPluginDescriptorFactory cannot be null");

    this.artifactPluginDescriptorLoader = artifactPluginDescriptorLoader;
    this.descriptorLoaderRepository = descriptorLoaderRepository;
  }

  @Override
  public ApplicationDescriptor create(File artifactFolder) throws ArtifactDescriptorCreateException {
    ApplicationDescriptor applicationDescriptor;
    final File mulePluginJsonFile = new File(artifactFolder, MULE_ARTIFACT_FOLDER + separator + MULE_APPLICATION_JSON);
    if (!mulePluginJsonFile.exists()) {
      throw new IllegalStateException("Artifact descriptor does not exists: " + mulePluginJsonFile);
    }
    applicationDescriptor = loadFromJsonDescriptor(artifactFolder, mulePluginJsonFile);

    return applicationDescriptor;
  }

  protected static String invalidClassLoaderModelIdError(File pluginFolder,
                                                         MuleArtifactLoaderDescriptor classLoaderModelLoaderDescriptor) {
    return format("The identifier '%s' for a class loader model descriptor is not supported (error found while reading plugin '%s')",
                  classLoaderModelLoaderDescriptor.getId(),
                  pluginFolder.getAbsolutePath());
  }

  private BundleDescriptor getBundleDescriptor(File appFolder, MuleApplicationModel muleApplicationModel) {
    BundleDescriptorLoader bundleDescriptorLoader;
    try {
      bundleDescriptorLoader =
          descriptorLoaderRepository.get(muleApplicationModel.getBundleDescriptorLoader().getId(), APP,
                                         BundleDescriptorLoader.class);
    } catch (LoaderNotFoundException e) {
      throw new ArtifactDescriptorCreateException(invalidBundleDescriptorLoaderIdError(appFolder, muleApplicationModel
          .getBundleDescriptorLoader()));
    }

    try {
      return bundleDescriptorLoader.load(appFolder, muleApplicationModel.getBundleDescriptorLoader().getAttributes(), APP);
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

  private ApplicationDescriptor loadFromJsonDescriptor(File applicationFolder, File muleApplicationJsonFile) {
    final MuleApplicationModel muleApplicationModel = getMuleApplicationJsonDescriber(muleApplicationJsonFile);

    final ApplicationDescriptor descriptor = new ApplicationDescriptor(applicationFolder.getName());
    descriptor.setArtifactLocation(applicationFolder);
    descriptor.setRootFolder(applicationFolder);
    descriptor.setBundleDescriptor(getBundleDescriptor(applicationFolder, muleApplicationModel));
    descriptor.setMinMuleVersion(new MuleVersion(muleApplicationModel.getMinMuleVersion()));
    descriptor.setRedeploymentEnabled(muleApplicationModel.isRedeploymentEnabled());
    muleApplicationModel.getDomain().ifPresent(domain -> {
      descriptor.setDomain(domain);
    });
    List<String> muleApplicationModelConfigs = muleApplicationModel.getConfigs();
    if (muleApplicationModelConfigs != null && !muleApplicationModelConfigs.isEmpty()) {
      descriptor.setConfigResources(muleApplicationModelConfigs.stream().map(configFile -> appendMuleFolder(configFile))
          .collect(toList()));
      List<File> configFiles = descriptor.getConfigResources()
          .stream()
          .map(config -> new File(applicationFolder, config)).collect(toList());
      descriptor.setConfigResourcesFile(configFiles.toArray(new File[configFiles.size()]));
      descriptor.setAbsoluteResourcePaths(configFiles.stream().map(configFile -> configFile.getAbsolutePath()).collect(toList())
          .toArray(new String[configFiles.size()]));
    } else {
      File configFile = new File(applicationFolder, appendMuleFolder(DEFAULT_CONFIGURATION_RESOURCE));
      descriptor.setConfigResourcesFile(new File[] {configFile});
      descriptor.setConfigResources(ImmutableList.<String>builder().add(DEFAULT_CONFIGURATION_RESOURCE_LOCATION).build());
      descriptor.setAbsoluteResourcePaths(new String[] {configFile.getAbsolutePath()});
    }

    muleApplicationModel.getClassLoaderModelLoaderDescriptor().ifPresent(classLoaderModelLoaderDescriptor -> {
      ClassLoaderModel classLoaderModel = getClassLoaderModel(applicationFolder, classLoaderModelLoaderDescriptor);
      descriptor.setClassLoaderModel(classLoaderModel);

      try {
        descriptor.setPlugins(createArtifactPluginDescriptors(classLoaderModel));
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    });
    File appClassesFolder = getAppClassesFolder(descriptor);
    // get a ref to an optional app props file (right next to the descriptor)
    setApplicationProperties(descriptor, new File(appClassesFolder, DEFAULT_ARTIFACT_PROPERTIES_RESOURCE));
    return descriptor;
  }

  private String appendMuleFolder(String configFile) {
    return MULE_CONFIG_FILES_FOLDER + File.separator + configFile;
  }

  private MuleApplicationModel getMuleApplicationJsonDescriber(File jsonFile) {
    try (InputStream stream = new FileInputStream(jsonFile)) {
      return new MuleApplicationModelJsonSerializer().deserialize(IOUtils.toString(stream));
    } catch (IOException e) {
      throw new IllegalArgumentException(format("Could not read extension describer on plugin '%s'", jsonFile.getAbsolutePath()),
                                         e);
    }
  }

  private ClassLoaderModel getClassLoaderModel(File applicationFolder,
                                               MuleArtifactLoaderDescriptor classLoaderModelLoaderDescriptor) {
    ClassLoaderModelLoader classLoaderModelLoader;
    try {
      classLoaderModelLoader =
          descriptorLoaderRepository.get(classLoaderModelLoaderDescriptor.getId(), APP, ClassLoaderModelLoader.class);
    } catch (LoaderNotFoundException e) {
      throw new ArtifactDescriptorCreateException(invalidClassLoaderModelIdError(applicationFolder,
                                                                                 classLoaderModelLoaderDescriptor));
    }

    final ClassLoaderModel classLoaderModel;
    try {
      classLoaderModel = classLoaderModelLoader.load(applicationFolder, classLoaderModelLoaderDescriptor.getAttributes(), APP);
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

  protected File getAppClassesFolder(ApplicationDescriptor descriptor) {
    return MuleFoldersUtil.getAppClassesFolder(descriptor.getName());
  }

  public void setApplicationProperties(ApplicationDescriptor desc, File appPropsFile) {
    // ugh, no straightforward way to convert a HashTable to a map
    Map<String, String> m = new HashMap<>();

    if (appPropsFile.exists() && appPropsFile.canRead()) {
      final Properties props;
      try {
        props = PropertiesUtils.loadProperties(appPropsFile.toURI().toURL());
      } catch (IOException e) {
        throw new IllegalArgumentException("Unable to obtain application properties file URL", e);
      }
      for (Object key : props.keySet()) {
        m.put(key.toString(), props.getProperty(key.toString()));
      }
    }

    // Override with any system properties prepended with "-O" for ("override"))
    Properties sysProps = System.getProperties();
    for (Map.Entry<Object, Object> entry : sysProps.entrySet()) {
      String key = entry.getKey().toString();
      if (key.startsWith(SYSTEM_PROPERTY_OVERRIDE)) {
        m.put(key.substring(SYSTEM_PROPERTY_OVERRIDE.length()), entry.getValue().toString());
      }
    }
    desc.setAppProperties(m);
  }
}
