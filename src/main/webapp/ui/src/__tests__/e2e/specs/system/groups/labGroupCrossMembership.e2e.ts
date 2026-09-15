import { expect } from "@playwright/test";
import { DocumentsClient } from "@/__tests__/e2e/api/clients/DocumentsClient";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { DYNAMIC_USER_PASSWORD, uniqueName } from "@/__tests__/e2e/testData";

test.describe("Lab Group cross-membership", { tag: tags.SYSTEM }, () => {
  test("A PI who is also a member of other PIs' Lab Groups sees one shared folder per group", async ({
    clientSysadmin,
    flowUserSession,
  }) => {
    const piA = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eCrossMemberPiA");
    const piB = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eCrossMemberPiB");

    await clientSysadmin.createGroup({
      displayName: uniqueName("e2eCrossGroupA"),
      type: "LAB_GROUP",
      users: [{ username: piA.username, roleInGroup: "PI" }],
    });
    await clientSysadmin.createGroup({
      displayName: uniqueName("e2eCrossGroupC"),
      type: "LAB_GROUP",
      users: [
        { username: piA.username, roleInGroup: "PI" },
        { username: piB.username, roleInGroup: "DEFAULT" },
      ],
    });

    const piASession = await flowUserSession(piA.username, DYNAMIC_USER_PASSWORD);
    await piASession.workspace.open();
    await piASession.workspace.toolbar.clickLabGroupShortcut();
    await expect(piASession.workspace.table.dataRows).toHaveCount(2);

    await clientSysadmin.createGroup({
      displayName: uniqueName("e2eCrossGroupD"),
      type: "LAB_GROUP",
      users: [
        { username: piB.username, roleInGroup: "PI" },
        { username: piA.username, roleInGroup: "DEFAULT" },
      ],
    });
    await clientSysadmin.createGroup({
      displayName: uniqueName("e2eCrossGroupB"),
      type: "LAB_GROUP",
      users: [{ username: piB.username, roleInGroup: "PI" }],
    });

    const piBSession = await flowUserSession(piB.username, DYNAMIC_USER_PASSWORD);
    await piBSession.workspace.open();
    await piBSession.workspace.toolbar.clickLabGroupShortcut();
    await expect(piBSession.workspace.table.dataRows).toHaveCount(3);
  });

  test("Searching by name finds only your own document, even when another PI has a same-named one", async ({
    clientSysadmin,
    apiContext,
    flowUserSession,
  }) => {
    const piA = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eCrossDocPiA");
    const piB = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eCrossDocPiB");
    const docName = uniqueName("e2eCrossDoc");
    await new DocumentsClient(apiContext, piA.apiKey).create({ name: docName });
    await new DocumentsClient(apiContext, piB.apiKey).create({ name: docName });

    const piBSession = await flowUserSession(piB.username, DYNAMIC_USER_PASSWORD);
    await piBSession.workspace.open();
    await piBSession.workspace.searchBar.search(docName);
    await expect(piBSession.workspace.table.dataRows).toHaveCount(1);
  });
});
