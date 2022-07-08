/*
 * Copyright (c) 2022 Broadcom.
 * The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Broadcom, Inc. - initial API and implementation
 *
 */

package org.eclipse.lsp.cobol.core.model;

import lombok.Value;

import java.util.Optional;

/**
 * This value class represents a copybook name with dialect info context. Display name is the name
 * as it is specified in the original COPY statement. Dialect type is the type of the dialect the
 * copybook statement belongs to (default is COBOL). Qualified data name is the display name
 * concatenated with the specified qualifier or usage (part of dialects processing).
 */
@Value
public class CopybookName {

  private static final String COBOL = "COBOL";

  String displayName;
  String dialectType;

  public CopybookName(String displayName) {
    this.displayName = displayName;
    this.dialectType = null;
  }

  public CopybookName(String displayName, String dialectType) {
    this.displayName = displayName;
    this.dialectType = dialectType;
  }

  public String getDialectType() {
    return Optional.ofNullable(dialectType).orElse(COBOL);
  }

  public String getQualifiedName() {
    return displayName;
  }
}
