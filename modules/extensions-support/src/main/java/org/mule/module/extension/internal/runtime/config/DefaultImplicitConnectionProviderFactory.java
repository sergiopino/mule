/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.extension.internal.runtime.config;

import static org.mule.api.config.MuleProperties.OBJECT_CONNECTION_MANAGER;
import static org.mule.module.extension.internal.introspection.utils.ImplicitObjectUtils.buildImplicitResolverSet;
import static org.mule.module.extension.internal.introspection.utils.ImplicitObjectUtils.getFirstImplicit;
import static org.mule.module.extension.internal.util.MuleExtensionUtils.getAllConnectionProviders;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.MuleRuntimeException;
import org.mule.api.connection.ConnectionProvider;
import org.mule.extension.api.introspection.RuntimeConfigurationModel;
import org.mule.extension.api.introspection.RuntimeConnectionProviderModel;
import org.mule.module.extension.internal.runtime.resolver.ResolverSet;

/**
 * Default implementation of {@link ImplicitConnectionProviderFactory}
 *
 * @since 4.0
 */
public final class DefaultImplicitConnectionProviderFactory implements ImplicitConnectionProviderFactory
{

    /**
     * {@inheritDoc}
     */
    @Override
    public <Config, Connector> ConnectionProvider<Config, Connector> createImplicitConnectionProvider(String configName, RuntimeConfigurationModel configurationModel, MuleEvent event)
    {
        RuntimeConnectionProviderModel implicitModel = (RuntimeConnectionProviderModel) getFirstImplicit(getAllConnectionProviders(configurationModel));

        if (implicitModel == null)
        {
            throw new IllegalStateException(String.format("Configuration '%s' of extension '%s' does not define a connection provider and none can be created automatically. Please define one.",
                                                          configName, configurationModel.getName()));
        }

        final ResolverSet resolverSet = buildImplicitResolverSet(implicitModel, event.getMuleContext().getExpressionManager());
        try
        {
            return new ConnectionProviderObjectBuilder(implicitModel, resolverSet, event.getMuleContext().getRegistry().get(OBJECT_CONNECTION_MANAGER)).build(event);
        }
        catch (MuleException e)
        {
            throw new MuleRuntimeException(e);
        }
    }
}