/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.component.config;

import org.mule.runtime.api.meta.AbstractAnnotatedObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import static java.util.Optional.of;
import static org.apache.commons.io.FileUtils.toFile;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;

/**
 * Artifact attributes configuration. This class represents a single configuration-attributes element
 * from the configuration.
 *
 * @since 4.0
 */
public class ConfigurationAttributes extends AbstractAnnotatedObject {

    private final List<ConfigurationAttribute> configurationAttributes;
    private String fileLocation;

    public ConfigurationAttributes(String fileLocation) throws ConfigurationAttributesException {
        this.fileLocation = fileLocation;
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        File attributesFile;
        URL resourceFromClassPath = contextClassLoader.getResource(fileLocation);
        if (resourceFromClassPath != null) {
            attributesFile = toFile(resourceFromClassPath);
        } else {
            attributesFile = new File(fileLocation);
        }
        if (!attributesFile.exists()) {
            throw new ConfigurationAttributesException(createStaticMessage("Couldn't find configuration attribute neither on classpath (%s) or in file system (%s"), this);
        }
        Properties properties = new Properties();
        try (InputStream is = new FileInputStream(attributesFile)) {
          properties.load(is);
           configurationAttributes = properties.keySet().stream().map(key -> {
                Object rawValue = properties.get(key);
                return new ConfigurationAttribute(of(this), (String) rawValue, (String) key);
            }).collect(Collectors.toList());
        } catch (Exception e) {
          throw new ConfigurationAttributesException(createStaticMessage("Couldn't read from file " + attributesFile.getAbsolutePath()), this);
        }
    }


    public List<ConfigurationAttribute> getConfigurationAttributes() {
        return configurationAttributes;
    }


}
