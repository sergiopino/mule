package org.mule.runtime.core.component.config;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.mule.tck.junit4.AbstractMuleTestCase;

import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;

public class ConfigurationAttributesResolverTestCase extends AbstractMuleTestCase {

    private static final String FIXED_VALUE = "fixedValue";
    private ConfigurationAttributesResolver resolver;

    @Before
    public void createResolver()
    {
        ConfigurationAttributesResolver parentResolver = new ConfigurationAttributesResolver(Optional.empty(), ImmutableList.<ConfigurationAttribute>builder()
                .add(new ConfigurationAttribute(this, "parent-key1", "parent-value1"))
                .add(new ConfigurationAttribute(this, "parent-key2", "parent-value2"))
                .add(new ConfigurationAttribute(this, "parent-complex-key1", "parent-complex-${parent-ket1}"))
                .build());
        resolver = new ConfigurationAttributesResolver(Optional.of(parentResolver),  ImmutableList.<ConfigurationAttribute>builder()
                .add(new ConfigurationAttribute(this, "child-key1", "child-value1"))
                .add(new ConfigurationAttribute(this, "child-key2", "child-value2"))
                .add(new ConfigurationAttribute(this, "child-complex-key1", "${child-key1}-${parent-complex-key1}"))
                .add(new ConfigurationAttribute(this, "invalid-key1", "${nonExistentKey}"))
                .build());
    }

    @Test
    public void resolveNoPlaceholder() {
        assertThat(resolver.resolveValue(FIXED_VALUE), is(FIXED_VALUE));
    }

    @Test
    public void nullValueReturnsNull() {
        assertThat(resolver.resolveValue(null), nullValue());
    }

    @Test
    public void resolveKeyInParent() {
        assertThat(resolver.resolveValue("parent-key-1"), is("parent-value1"));
    }

    @Test
    public void resolveKeyInChild() {
        assertThat(resolver.resolveValue("child-key-1"), is("child-value1"));
    }

    @Test
    public void resolveParentComplexKey() {
        assertThat(resolver.resolveValue("parent-complex-key1"), is("parent-complex-parent-value1"));
    }

    @Test
    public void resolveChildComplexKey() {
        assertThat(resolver.resolveValue("child-complex-key1"), is("child-value1-parent-complex-parent-value1"));
    }

    @Test(expected = Exception.class)
    public void resolveInvalidKey() {
        resolver.resolveValue("invalid-key1");
    }

}