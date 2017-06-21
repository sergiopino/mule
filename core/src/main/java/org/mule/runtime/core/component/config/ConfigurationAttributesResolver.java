package org.mule.runtime.core.component.config;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.mule.runtime.api.exception.MuleRuntimeException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;

/**
 * Resolves attribute placeholders.
 * <p>
 * It will delegate the placeholder resolution to it's parent if it weren't able to resolve a value by itself.
 *
 * @since 4.0
 */
public class ConfigurationAttributesResolver {

    public static final String PLACEHOLDER_PREFIX = "${";
    public static final String PLACEHOLDER_SUFFIX = "}";
    private final Optional<ConfigurationAttributesResolver> parentResolver;
    private final List<ConfigurationAttribute> deploymentConfigurationAttributes;
    private Cache<String, String> resolutionCache = CacheBuilder.<String, String>newBuilder().build();

    public ConfigurationAttributesResolver(Optional<ConfigurationAttributesResolver> parentResolver, List<ConfigurationAttribute> deploymentConfigurationAttributes) {
        this.parentResolver = parentResolver;
        this.deploymentConfigurationAttributes = deploymentConfigurationAttributes;
    }

    public String resolveValue(String value) {
        try {
            return this.resolutionCache.get(value, () -> {
                if (value.indexOf(PLACEHOLDER_PREFIX) == -1) {
                    return value;
                }
                return resolvePlaceholder(value);
            });
        } catch (ExecutionException e) {
            throw new MuleRuntimeException(createStaticMessage("Failure processing configuration attribute " + value));
        }
    }

    private String resolvePlaceholder(String value) {
        int initialIndex = value.indexOf(PLACEHOLDER_PREFIX);
        int finalIndex = value.indexOf(PLACEHOLDER_SUFFIX);
        if (finalIndex == -1) {
            return value;
        }
        String placeholderKey = value.substring(initialIndex + PLACEHOLDER_PREFIX.length(), finalIndex);
        String newValue = value.replace(PLACEHOLDER_PREFIX + placeholderKey + PLACEHOLDER_SUFFIX, resolveValue(placeholderKey));
        return resolvePlaceholder(newValue);
    }

}
