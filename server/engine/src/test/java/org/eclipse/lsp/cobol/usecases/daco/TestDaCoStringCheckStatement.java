/*
 * Copyright (c) 2022 DAF Trucks NV.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * DAF Trucks NV – implementation of DaCo COBOL statements
 * and DAF development standards
 */
package org.eclipse.lsp.cobol.usecases.daco;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.eclipse.lsp.cobol.core.model.ErrorSource;
import org.eclipse.lsp.cobol.usecases.DialectConfigs;
import org.eclipse.lsp.cobol.usecases.engine.UseCaseEngine;
import org.eclipse.lsp.cobol.utils.Fixtures;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;

/**
 * Tests the DaCo STRING CHECK statement
 */
class TestDaCoStringCheckStatement {

    private static final String TEXT =
            "        IDENTIFICATION DIVISION. \r\n"
                    + "        PROGRAM-ID. test1. \r\n"
                    + "        DATA DIVISION. \r\n"
                    + "        WORKING-STORAGE SECTION. \r\n"
                    + "        01 {$*WS-AREA}. \r\n"
                    + "           03 {$*AREA-XW1}. \r\n"
                    + "             05 {$*DSAPRO-XL1}. \r\n"
                    + "               07 FILLER               PIC X(5)    VALUE 'REMBD'. \r\n"
                    + "        PROCEDURE DIVISION. \r\n"
                    + "            STRING CHECK EMA {$DSAPRO-XL1}. \r\n"
                    + "            STRING CHECK EMA 'a-b.c@paccar.com'. \r\n"
                    // Negative tests
                    + "            STRING CHECK EMA {GBR4|1}. \r\n"
                    + "            STRING CHECK EMA \r\n"
                    + "            {'a-rewrsdsfbvdfdf.dfdfdewrewrwerewrwrrwrewrw@daftrucks.com'|3}. \r\n";

    @Test
    void test() {

        UseCaseEngine.runTestForDiagnostics(
                TEXT,
                ImmutableList.of(Fixtures.subschemaCopy("")),
                ImmutableMap.of(
                        "1",
                        new Diagnostic(
                                new Range(),
                                "Variable GBR4 is not defined",
                                DiagnosticSeverity.Error,
                                ErrorSource.PARSING.getText()),
                        "2",
                        new Diagnostic(
                                new Range(),
                                "Syntax error on '.' expected {ALL, DFHRESP, DFHVALUE, FALSE, HIGH-VALUE, HIGH-VALUES, LOW-VALUE, LOW-VALUES, NULL, NULLS, QUOTES, SPACE, SPACES, TRUE, ZERO, ZEROES, ZEROS, '01-49', '66', '77', '88', INTEGERLITERAL, NUMERICLITERAL, NONNUMERICLITERAL, IDENTIFIER, FINALCHARSTRING}",
                                DiagnosticSeverity.Error,
                                ErrorSource.PARSING.getText()),
                        "3",
                        new Diagnostic(
                                new Range(),
                                "Max length limit of 55 bytes allowed for email.",
                                DiagnosticSeverity.Error,
                                ErrorSource.DIALECT.getText())),
                ImmutableList.of(), DialectConfigs.getDaCoAnalysisConfig());
    }
}
