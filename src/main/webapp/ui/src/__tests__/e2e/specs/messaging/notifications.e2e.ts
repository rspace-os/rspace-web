import { expect } from "@playwright/test";
import { DocumentsClient } from "@/__tests__/e2e/api/clients/DocumentsClient";
import { ShareClient } from "@/__tests__/e2e/api/clients/ShareClient";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { DYNAMIC_USER_PASSWORD, uniqueName } from "@/__tests__/e2e/testData";

test.describe("Notifications", () => {
  test("As a user, I can delete displayed notifications after opening Messages without deleting messages", async ({
    apiContext,
    clientSysadmin,
    flowMessagingSession,
  }) => {
    const recipient = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eNotifRecipient");
    const sender = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eNotifSender");
    const group = await clientSysadmin.createGroup({
      displayName: uniqueName("e2eNotifGroup"),
      type: "LAB_GROUP",
      users: [
        { username: recipient.username, roleInGroup: "PI" },
        { username: sender.username, roleInGroup: "DEFAULT" },
      ],
    });
    const docName = uniqueName("e2eNotifDoc");
    const doc = await new DocumentsClient(apiContext, sender.apiKey).create({ name: docName });
    await new ShareClient(apiContext, sender.apiKey).shareWithGroup([doc.id], group.id, "READ");
    const text = uniqueName("e2eRetainedMessage");
    const senderSession = await flowMessagingSession(sender.username, DYNAMIC_USER_PASSWORD);
    await senderSession.sendMessage.openFromToolbar();
    await senderSession.sendMessage.send({ to: recipient.username, text });

    const session = await flowMessagingSession(recipient.username, DYNAMIC_USER_PASSWORD);
    const page = session.workspace.browserPage;
    const inbox = await session.workspace.openReceivedMessages();
    await expect(inbox.message(text)).toBeVisible();
    const messageRowId = await inbox.message(text).getAttribute("id");
    expect(messageRowId).toMatch(/^messageID_\d+$/);
    await inbox.close();
    // The legacy JSP keeps message rows mounted after closing the dialog.
    const retainedMessage = page.locator(`#${messageRowId}`);
    await expect(retainedMessage).toBeAttached();
    await expect(retainedMessage).toBeHidden();

    await session.notifications.open();
    await expect(session.notifications.row(docName)).toBeVisible();
    const displayedIds = await session.notifications.root
      .locator('tr.notificationRow[id^="notificnID_"]')
      .evaluateAll((rows) => rows.map((row) => row.id.replace("notificnID_", "")));
    expect(displayedIds.length).toBeGreaterThan(0);
    const responsePromise = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === "/dashboard/ajax/markAsRead" && response.request().method() === "POST",
    );
    const refreshPromise = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === "/dashboard/ajax/listNotifications" &&
        response.request().redirectedFrom() !== null,
    );
    await session.notifications.root.getByRole("link", { name: "Delete all displayed", exact: true }).click();
    const response = await responsePromise;
    const submittedIds = new URLSearchParams(response.request().postData() ?? "").getAll("id[]");
    expect(submittedIds.sort()).toEqual(displayedIds.sort());
    expect(submittedIds).not.toContain(messageRowId?.replace("messageID_", ""));
    expect(response.status()).toBe(302);
    expect((await refreshPromise).ok()).toBe(true);
    await expect(session.notifications.emptyState).toBeVisible();
    await session.notifications.close();
    await session.notifications.open();
    await expect(session.notifications.emptyState).toBeVisible();
    await session.notifications.close();
    const reopenedInbox = await session.workspace.openReceivedMessages();
    await expect(reopenedInbox.message(text)).toBeVisible();
  });

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

    await colleagueSession.workspace.open();
    await colleagueSession.notifications.open();
    await expect(colleagueSession.notifications.emptyState).toBeVisible();
  });
});
