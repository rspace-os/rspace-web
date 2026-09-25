import { createDynamicUser, type DynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { DYNAMIC_APP_USER_LAST_NAME, dynamicUserTest } from "@/__tests__/e2e/fixtures/dynamicUser";
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

/**
 * The owner PI requests a collaboration with the recipient PI, who accepts. RSpace names the new group
 * "<originator last name>-<recipient last name>-collabGroup" (CollabGroupShareRequestCreateHandler);
 * it is renamed to a unique name so parallel tests can't pick each other's group.
 */
async function createCollaborationGroup(
  owner: DocumentSession,
  recipientSession: DocumentSession,
  ownerLabId: number,
  recipient: DynamicUser,
  recipientLastName: string,
): Promise<{ name: string; id: number }> {
  await owner.groupDetails.openGroup(ownerLabId);
  await owner.groupDetails.requestCollaborationGroup(recipient.username);
  await recipientSession.workspace.open();
  const messages = await recipientSession.workspace.openReceivedMessages();
  await messages.acceptFirstRequest();
  await messages.close();
  const initialName = `${DYNAMIC_APP_USER_LAST_NAME}-${recipientLastName}-collabGroup`;
  const id = await recipientSession.directory.findGroupIdForUser(recipient.username, initialName);
  await recipientSession.groupDetails.openGroup(id);
  const name = alphaNumericUnique("ShareCollaboration");
  await recipientSession.groupDetails.rename(name);
  await owner.refreshAfterGroupChange();
  await recipientSession.refreshAfterGroupChange();
  return { name, id };
}

/** The owner is the test's appUser, already logged in before the groups exist, so its session is refreshed. */
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
      const { name, id: groupId } =
        type === "collaboration"
          ? await createCollaborationGroup(ownerSession, recipientSession, ownerLab.id, recipient, recipientLastName)
          : ownerLab;
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
