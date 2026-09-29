import { expect } from "@playwright/test";
import { DocumentsClient } from "@/__tests__/e2e/api/clients/DocumentsClient";
import { FoldersClient } from "@/__tests__/e2e/api/clients/FoldersClient";
import { ShareClient } from "@/__tests__/e2e/api/clients/ShareClient";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { DYNAMIC_USER_PASSWORD, uniqueName } from "@/__tests__/e2e/testData";

test.describe("Messaging", () => {
  test("As a user, I can message another user, read their reply and dismiss it", async ({
    clientSysadmin,
    flowMessagingSession,
  }) => {
    const sender = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eMsgSender");
    const recipient = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eMsgRecipient");
    const text = uniqueName("e2eMessage");
    const replyText = uniqueName("e2eReply");

    const senderSession = await flowMessagingSession(sender.username, DYNAMIC_USER_PASSWORD);
    await senderSession.sendMessage.openFromToolbar();
    await senderSession.sendMessage.send({ to: recipient.username, text });

    const recipientSession = await flowMessagingSession(recipient.username, DYNAMIC_USER_PASSWORD);
    const inbox = await recipientSession.workspace.openReceivedMessages();
    await expect(inbox.message(text)).toContainText(sender.fullName);
    await inbox.reply(text, replyText);

    await senderSession.workspace.open();
    const senderInbox = await senderSession.workspace.openReceivedMessages();
    await expect(senderInbox.message(replyText)).toContainText(recipient.fullName);
    await senderInbox.dismiss(replyText);

    await senderSession.workspace.open();
    const reopened = await senderSession.workspace.openReceivedMessages();
    await expect(reopened.message(replyText)).toHaveCount(0);
  });

  test("As a PI, I am offered group and review requests where a member is not, and can request a review of an entry", async ({
    apiContext,
    clientSysadmin,
    flowMessagingSession,
  }) => {
    const pi = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eMsgPi");
    const member = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eMsgMember");
    const group = await clientSysadmin.createGroup({
      displayName: uniqueName("e2eMsgGroup"),
      type: "LAB_GROUP",
      users: [
        { username: pi.username, roleInGroup: "PI" },
        { username: member.username, roleInGroup: "DEFAULT" },
      ],
    });
    const notebook = await new FoldersClient(apiContext, pi.apiKey).create({
      name: uniqueName("e2eMsgNotebook"),
      notebook: true,
    });
    const entryName = uniqueName("e2eMsgEntry");
    await new DocumentsClient(apiContext, pi.apiKey).create({ name: entryName, parentFolderId: notebook.id });
    await new ShareClient(apiContext, pi.apiKey).shareWithGroup([notebook.id], group.id, "EDIT");

    const memberSession = await flowMessagingSession(member.username, DYNAMIC_USER_PASSWORD);
    await memberSession.sendMessage.openFromToolbar();
    await expect(memberSession.sendMessage.typeOptions).toHaveText(["Basic message"]);
    await memberSession.sendMessage.cancel();

    const piSession = await flowMessagingSession(pi.username, DYNAMIC_USER_PASSWORD);
    await piSession.sendMessage.openFromToolbar();
    await expect(piSession.sendMessage.typeOptions).toHaveText(["Basic message", "Create a Collaboration Group"]);
    await piSession.sendMessage.cancel();

    await piSession.workspace.open(notebook.id);
    await piSession.workspace.table.openRecord(entryName);
    await piSession.notebook.isLoaded();
    await piSession.sendMessage.openFromToolbar();
    await expect(piSession.sendMessage.typeOptions).toHaveText([
      "Basic message",
      "Review document",
      "Create a Collaboration Group",
    ]);
    const reviewText = uniqueName("e2eReviewRequest");
    await piSession.sendMessage.send({ type: "Review document", to: member.username, text: reviewText });

    await memberSession.workspace.open();
    const inbox = await memberSession.workspace.openReceivedMessages();
    await expect(inbox.message(reviewText)).toContainText("Review document");
    await expect(inbox.message(reviewText).getByRole("link", { name: entryName })).toBeVisible();
  });

  test("As a sysadmin, I can message all users, who see a sanitised announcement", async ({
    clientSysadmin,
    flowMessagingSession,
    flowSysadminAnnouncement,
  }) => {
    const user = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eMsgAnnouncementReader");
    const text = uniqueName("e2eAnnouncement");
    const url = "http://www.bbc.co.uk";
    await flowSysadminAnnouncement(text, `${url} <script>alert(1)</script> <strong>strongtext</strong>`);

    const session = await flowMessagingSession(user.username, DYNAMIC_USER_PASSWORD);
    const announcement = session.announcement.withText(text);
    await expect(announcement.getByRole("link", { name: url })).toHaveAttribute("rel", "nofollow");
    await expect(announcement.getByRole("strong").filter({ hasText: "strongtext" })).toBeVisible();
    await expect(announcement).not.toContainText("alert(1)");

    await session.sendMessage.openFromToolbar();
    await expect(session.sendMessage.typeOptions).toHaveText(["Basic message"]);
  });
});
