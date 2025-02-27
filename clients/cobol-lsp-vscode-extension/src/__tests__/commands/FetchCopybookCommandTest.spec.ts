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
 *   Broadcom, Inc. - initial API and implementation
 */

import { fetchCopybookCommand } from "../../commands/FetchCopybookCommand";
import { CopybookDownloadService, CopybookName } from "../../services/copybook/CopybookDownloadService";
import { TelemetryService } from "../../services/reporter/TelemetryService";

jest.mock("../../services/reporter/TelemetryService");
jest.mock("../../services/copybook/CopybookDownloadService");
jest.mock('@zowe/zowe-explorer-api/lib/vscode', () => {
    return {
      ZoweVsCodeExtension: jest.fn()
    };
  });
  
const copybookDownloadService: CopybookDownloadService = new CopybookDownloadService();
const copybook: string = "cobyBookTest";
const progName: string = "progNameTest";

test("Test fetchCopybookCommand calls telementry services and coprbook download service with proper input", () => {
    expect(fetchCopybookCommand).toBeTruthy();
    fetchCopybookCommand(copybook, copybookDownloadService, progName);
    expect(TelemetryService.registerEvent).toBeCalledWith("Fetch copybook", ["COBOL", "copybook", "quickfix"], "The user tries to resolve a copybook that is not currently found");
    expect(copybookDownloadService.downloadCopybooks).toBeCalledWith(progName, [new CopybookName(copybook, "COBOL")], false);
});
