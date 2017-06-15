/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.json.validation;

import org.mule.tck.junit4.AbstractMuleContextTestCase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;

import java.util.Set;

import org.junit.Before;
import org.junit.Test;

public class JsonDateValidationTestCase extends AbstractMuleContextTestCase
{

  private JsonSchemaValidator validator;

  @Before
  public void before() {
    validator = JsonSchemaValidator.builder()
            .setSchemaLocation("/schema/date.json")
            .build();
  }

  @Test
  public void invalidDate() throws Exception {
    String data = "{\"dateSample\" : \"2017asc-12-32\", \"datetimeSample\" : \"2017-12-30T00:00:00.000Z\"}";
    ObjectMapper mapper = new ObjectMapper();
    JsonNode node = mapper.readTree(data);
    JsonSchemaFactory factory = new JsonSchemaFactory();
    JsonSchema schema = factory.getSchema(node);
    Set<ValidationMessage> errors = schema.validate(node);
    errors.isEmpty();
  }
}
