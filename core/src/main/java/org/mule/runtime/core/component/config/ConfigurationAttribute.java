package org.mule.runtime.core.component.config;

import static org.mule.runtime.api.util.Preconditions.checkNotNull;

/**
 * Represents a configuration attribute.
 *
 * @since 4.0
 */
public class ConfigurationAttribute {

    private Object source;
    private String rawValue;
    private String key;

    /**
     * Creates a new configuration value
     *
     * @param source the source of this configuration attribute. For instance, it may be an {@link org.mule.runtime.api.meta.AnnotatedObject} if it's source was defined
     *               in the artifact configuration or it may be the deployment properties configured at deployment time.
     * @param key the key of the configuration attribute to reference it.
     * @param rawValue the plain configuration value without resolution. A configuration value may contain reference to other configuration attributes.
     */
    public ConfigurationAttribute(Object source, String key, String rawValue) {
        checkNotNull(source, "source cannot be null");
        checkNotNull(rawValue, "rawValue cannot be null");
        checkNotNull(key, "key cannot be null");
        this.source = source;
        this.rawValue = rawValue;
        this.key = key;
    }


    /**
     * @return the source of this configuration attribute. For instance, it may be an {@link org.mule.runtime.api.meta.AnnotatedObject} if it's source was defined
     *               in the artifact configuration or it may be the deployment properties configured at deployment time.
     */
    public Object getSource() {
        return source;
    }

    /**
     * @return the plain configuration value without resolution. A configuration value may contain reference to other configuration attributes.
     */
    public String getRawValue() {
        return rawValue;
    }

    /**
     * @return the key of the configuration attribute to reference it.
     */
    public String getKey() {
        return key;
    }

}
