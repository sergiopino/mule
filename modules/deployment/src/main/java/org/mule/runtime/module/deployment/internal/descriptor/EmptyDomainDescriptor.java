/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.deployment.internal.descriptor;

import java.io.File;

/**
 * Represents the description of a domain when none is given
 */
public class EmptyDomainDescriptor extends DomainDescriptor {

  public EmptyDomainDescriptor(File domainLocation) {
    super(domainLocation.getName());
    this.setArtifactLocation(domainLocation);
  }
}