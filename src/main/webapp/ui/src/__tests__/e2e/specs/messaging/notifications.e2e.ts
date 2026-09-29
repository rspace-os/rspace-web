import { expect } from "@playwright/test";
import { DocumentsClient } from "@/__tests__/e2e/api/clients/DocumentsClient";
import { ShareClient } from "@/__tests__/e2e/api/clients/ShareClient";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { DYNAMIC_USER_PASSWORD, uniqueName } from "@/__tests__/e2e/testData";

test.describe("Notifications", () => {
  test("As a group member, I am notified when a colleague shares, and then deletes, a document", async ({
    apiContext,
    clientSysadmin,
    flowMessagingSession,
  }) => {
    const pi = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eNotifPi");
    const owner = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eNotifOwner");
    const colleague = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eNotifColleague");
    const group = await clientSysadmin.createGroup({
      displayName: uniqueName("e2eNotifGroup"),
      type: "LAB_GROUP",
      users: [
        { username: pi.username, roleInGroup: "PI" },
        { username: owner.username, roleInGroup: "DEFAULT" },
        { username: colleague.username, roleInGroup: "DEFAULT" },
      ],
    });
    const docName = uniqueName("e2eNotifDoc");
    const ownerDocuments = new DocumentsClient(apiContext, owner.apiKey);
    const doc = await ownerDocuments.create({ name: docName });
    await new ShareClient(apiContext, owner.apiKey).shareWithGroup([doc.id], group.id, "READ");

    const piSession = await flowMessagingSession(pi.username, DYNAMIC_USER_PASSWORD);
    await piSession.notifications.open();
    const shared = piSession.notifications.row(`${docName} shared by ${owner.username}`);
    await expect(shared).toBeVisible();
    await shared.getByRole("link", { name: `${docName} (${doc.globalId})` }).click();
    await piSession.document.isLoaded();
    await expect(piSession.document.header.name).toHaveText(docName);

    await ownerDocuments.deleteById(doc.id);

    await piSession.workspace.open();
    await piSession.notifications.open();
    await expect(piSession.notifications.row(`${owner.username} deleted '${docName}'`)).toBeVisible();

    const colleagueSession = await flowMessagingSession(colleague.username, DYNAMIC_USER_PASSWORD);
    await colleagueSession.notifications.open();
    await expect(colleagueSession.notifications.row(`${owner.username} stopped sharing ${docName}`)).toBeVisible();
    await colleagueSession.notifications.deleteAll();
    await expect(colleagueSession.notifications.row(docName)).toHaveCount(0);
  });
});
