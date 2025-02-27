/*
 * Copyright (c) 2020 Broadcom.
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
package org.eclipse.lsp.cobol.usecases.idms;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.eclipse.lsp.cobol.core.model.ErrorSource;
import org.eclipse.lsp.cobol.usecases.DialectConfigs;
import org.eclipse.lsp.cobol.usecases.engine.UseCaseEngine;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.eclipse.lsp4j.DiagnosticSeverity.Error;

/** These test for variations other idms SECTION definitions */
class TestIdmsSections {

  private static final String BOILERPLATE =
      "        IDENTIFICATION DIVISION.\n"
          + "        PROGRAM-ID. test1.\n"
          + "        ENVIRONMENT DIVISION.\n";

  private static final String IDMSSS_WITH_ALL_CLAUSES =
      "        IDMS-CONTROL SECTION.\n"
          + "            PROTOCOL. MODE ABC\n"
          + "            SUBSCHEMA-NAMES LENGTH 18\n"
          + "            IDMS-RECORDS WITHIN WORKING-STORAGE.\n"
          + "        DATA DIVISION.\n"
          + "       SCHEMA SECTION.\n"
          + "            DB EMPSS01 WITHIN ENPSCHM VERSION 1234.\n";

  /** This test should fail because the subschema-name length is max 8 characters */
  private static final String IDMSSS_NO_CS_NO_VERSION =
      "        DATA DIVISION.\n"
          + "        SCHEMA SECTION.\n"
          + "            DB EMPSS012 WITHIN EMPSCHM.\n";

  private static final String IDMSMS_WITH_ALL_CLAUSES =
      "        DATA DIVISION.\n"
          + "        SCHEMA SECTION.\n"
          + "            DB EMPSS012 WITHIN EMPSCHM.\n"
          + "        MAP SECTION.\n"
          + "        MAX FIELD LIST IS 12 \n"
          + "        MAP {$*MAP1} TYPE IS EXTENDED PAGING.\n"
          + "        MAP {$*MAP2}\n"
          + "        MAP {$*MAP3} TYPE STANDARD.\n";

  private static final String IDMSMS_WITH_ALL_CLAUSES_ERRORS =
      "        DATA DIVISION.\n"
          + "        SCHEMA SECTION.\n"
          + "            DB {EMPSS012X|1} WITHIN {EMPSCHMXX|2}.\n"
          + "        MAP SECTION.\n"
          + "        MAX FIELD LIST IS 12 \n"
          + "        MAP {$*MAP1TOOLONG|3} TYPE IS EXTENDED PAGING.\n"
          + "        MAP {$*MAP2}\n"
          + "        MAP {$*MAP3} TYPE STANDARD.\n";

  private static Stream<String> textsToTest() {
    return Stream.of(
        BOILERPLATE + IDMSSS_WITH_ALL_CLAUSES,
        BOILERPLATE + IDMSSS_NO_CS_NO_VERSION,
        BOILERPLATE + IDMSMS_WITH_ALL_CLAUSES,
        BOILERPLATE + IDMSMS_WITH_ALL_CLAUSES_ERRORS);
  }

  @ParameterizedTest
  @MethodSource("textsToTest")
  void test(String text) {
    UseCaseEngine.runTest(
        text,
        ImmutableList.of(),
        ImmutableMap.of(
            "1",
            new Diagnostic(
                new Range(),
                "Max length limit of 8 bytes allowed for subschema name.",
                Error,
                ErrorSource.DIALECT.getText()),
            "2",
            new Diagnostic(
                new Range(),
                "Max length limit of 8 bytes allowed for schema name.",
                Error,
                ErrorSource.DIALECT.getText()),
            "3",
            new Diagnostic(
                new Range(),
                "Max length limit of 8 bytes allowed for map name.",
                Error,
                ErrorSource.DIALECT.getText())),
        ImmutableList.of(),
        DialectConfigs.getIDMSAnalysisConfig());
  }
}
