import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { dynamicUserTest } from "@/__tests__/e2e/fixtures/dynamicUser";
import { alphaNumericUnique } from "@/__tests__/e2e/testData";
import type { DocumentSession } from "./documentSessions";

type SharingGroup = {
  name: string;
  sharedFolderId: number;
  ownerHomeFolderId: number;
  owner: DocumentSession;
  recipient: DocumentSession;
  recipientName: string;
  recipientUsername: string;
};

/** Fresh sessions are opened after group creation so Shiro sees the new permissions. */
export const test = dynamicUserTest.extend<{
  flowSharingGroup: (type?: "lab" | "collaboration") => Promise<SharingGroup>;
}>({
  flowSharingGroup: async ({ clientSysadmin, appUser, flowDocumentSession }, use) => {
    await use(async (type = "lab") => {
      const recipientLastName = alphaNumericUnique("ShareRecipient");
      const recipient = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eShareRecipient", recipientLastName);
      const ownerLab = await clientSysadmin.createGroup({
        displayName: alphaNumericUnique("ShareOwnerLab"),
        type: "LAB_GROUP",
        users: [
          { username: appUser.username, roleInGroup: "PI" },
          ...(type === "lab" ? [{ username: recipient.username, roleInGroup: "RS_LAB_ADMIN" as const }] : []),
        ],
      });
      if (type === "collaboration") {
        await clientSysadmin.createGroup({
          displayName: alphaNumericUnique("ShareRecipientLab"),
          type: "LAB_GROUP",
          users: [{ username: recipient.username, roleInGroup: "PI" }],
        });
      }
      const ownerSession = await flowDocumentSession(appUser);
      await ownerSession.refreshAfterGroupChange();
      const recipientSession = await flowDocumentSession(recipient);
      let name = ownerLab.name;
      let groupId = ownerLab.id;
      if (type === "collaboration") {
        await ownerSession.groupDetails.openGroup(ownerLab.id);
        await ownerSession.groupDetails.requestCollaborationGroup(recipient.username);
        await recipientSession.workspace.open();
        const messages = await recipientSession.workspace.openReceivedMessages();
        await messages.acceptFirstRequest();
        await messages.close();
        name = `DynamicUser-${recipientLastName}-collabGroup`;
        const id = await recipientSession.directory.findGroupIdForUser(recipient.username, name);
        groupId = id;
        await recipientSession.groupDetails.openGroup(id);
        name = alphaNumericUnique("ShareCollaboration");
        await recipientSession.groupDetails.rename(name);
        await ownerSession.refreshAfterGroupChange();
        await recipientSession.refreshAfterGroupChange();
      }
      await ownerSession.groupDetails.openGroup(groupId);
      const ownerHomeFolderId = await ownerSession.groupDetails.homeFolderId(appUser.username);
      const sharedFolderId = await ownerSession.groupDetails.sharedFolderId();
      await ownerSession.workspace.openSharedFolder({ name, sharedFolderId });
      return {
        name,
        sharedFolderId,
        ownerHomeFolderId,
        owner: ownerSession,
        recipient: recipientSession,
        recipientName: recipient.fullName,
        recipientUsername: recipient.username,
      };
    });
  },
});
