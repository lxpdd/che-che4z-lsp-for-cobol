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
 package org.eclipse.lsp.cobol.core.engine.dialects.daco;

import java.util.Optional;

/** DaCo related utilites */
public class DaCoHelper {

  /** extract suffix (two characters after -\w substring) of
   *  @param name of variable
   *  @return suffix or null if not found
   */
  public static Optional<String> extractSuffix(String name) {
    if (name.length() < 3) {
      return Optional.empty();
    }
    int l2 = name.length() - 2;
    if (name.charAt(l2) == '-') {
      return Optional.of("");
    }
    if (name.length() < 5) {
      return Optional.empty();
    }
    int l4 = name.length() - 4;
    if (name.charAt(l4) == '-') {
      return Optional.of(name.substring(l2));
    }
    return Optional.empty();
  }
}
