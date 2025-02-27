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
package org.eclipse.lsp.cobol.usecases.daco;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.eclipse.lsp.cobol.core.engine.dialects.daco.DaCoDialect;
import org.eclipse.lsp.cobol.core.engine.dialects.idms.IdmsDialect;
import org.eclipse.lsp.cobol.core.engine.symbols.CopyDefinition;
import org.eclipse.lsp.cobol.core.model.tree.CopyNode;
import org.eclipse.lsp.cobol.core.model.tree.NodeType;
import org.eclipse.lsp.cobol.positive.CobolText;
import org.eclipse.lsp.cobol.usecases.DialectConfigs;
import org.eclipse.lsp.cobol.usecases.engine.UseCaseEngine;
import org.eclipse.lsp.cobol.usecases.engine.UseCaseUtils;
import org.eclipse.lsp.cobol.utils.Fixtures;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Location;
import org.junit.jupiter.api.Test;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

/** COPY MAID copybook definitions should have correct URIs */
class TestCopyMaidWithQualifierHasCorrectDefinition {
  private static final String TEXT =
      "       IDENTIFICATION DIVISION.\n"
          + "       PROGRAM-ID.    TEST.\n"
          + "       ENVIRONMENT DIVISION.\n"
          + "       DATA DIVISION.\n"
          + "       WORKING-STORAGE SECTION.\n"
          + "       01 COPY MAID {~BHTRGL!DaCo`BHTRGL_ABC} ABC.\n"
          + "       01 COPY MAID {~BHTRGL!DaCo`BHTRGL}.\n"
          + "       PROCEDURE DIVISION.\n"
          + "           DISPLAY {$BHTRGL1}.\n"
          + "           DISPLAY {$BHTRGL2}.";

  private static final String BHTRGL = "1        01 {$*BHTRGL1} PIC X.\n";
  private static final String BHTRGL_ABC = "1        01 {$*BHTRGL2} PIC X.\n";

  @Test
  void test() {
    CobolText idmsCopybook = Fixtures.subschemaCopy("");
    ImmutableList<CobolText> copybooks = ImmutableList.of(
            new CobolText("BHTRGL", DaCoDialect.NAME, BHTRGL),
            new CobolText("BHTRGL_ABC", DaCoDialect.NAME, BHTRGL_ABC),
            idmsCopybook
    );
    ImmutableMap<String, Diagnostic> diagnostics = ImmutableMap.of();
    assertThat(
        UseCaseEngine.runTestForDiagnostics(TEXT, copybooks, diagnostics, ImmutableList.of(),
                        DialectConfigs.getDaCoAnalysisConfig())
            .getRootNode()
            .getDepthFirstStream()
            .filter(it -> it.getNodeType() == NodeType.COPY)
            .map(CopyNode.class::cast)
            .map(CopyNode::getDefinition)
            .map(CopyDefinition::getLocation)
            .map(Location::getUri)
            .collect(toList()),
        containsInAnyOrder(UseCaseUtils.toURI("BHTRGL", "DaCo"),
            UseCaseUtils.toURI("BHTRGL_ABC", "DaCo"),
            UseCaseUtils.toURI(idmsCopybook.getFileName(), IdmsDialect.NAME)));
  }
}
