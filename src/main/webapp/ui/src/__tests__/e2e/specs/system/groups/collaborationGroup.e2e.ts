import { expect } from "@playwright/test";
import type { SelfServicePiActor } from "@/__tests__/e2e/fixtures/flows";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

function collaborationGroupName(originator: SelfServicePiActor, recipient: SelfServicePiActor): string {
  return `${originator.lastName}-${recipient.lastName}-collabGroup`;
}

async function giveOwnLabGroup(pi: SelfServicePiActor, namePrefix: string): Promise<void> {
  await pi.selfServiceLabGroup.open();
  await pi.selfServiceLabGroup.createGroup(uniqueName(namePrefix));
}

async function createCollaborationGroup(
  piA: SelfServicePiActor,
  piB: SelfServicePiActor,
): Promise<{ groupId: number; groupName: string }> {
  await giveOwnLabGroup(piA, "e2eCollabPiA");
  await giveOwnLabGroup(piB, "e2eCollabPiB");

  await piA.groupDetails.requestCollaborationGroup(piB.username);

  await piB.workspace.open();
  const messages = await piB.workspace.openReceivedMessages();
  await messages.acceptFirstRequest();
  await messages.close();

  const groupName = collaborationGroupName(piA, piB);
  const groupId = await piB.directory.findGroupIdForUser(piB.username, groupName);
  return { groupId, groupName };
}

test.describe("Collaboration Group creation", { tag: tags.SYSTEM }, () => {
  test("As two PIs, we can create a Collaboration Group via a message request", async ({ flowSelfServicePi }) => {
    const piA = await flowSelfServicePi();
    const piB = await flowSelfServicePi();

    const { groupId, groupName } = await createCollaborationGroup(piA, piB);
    await piB.groupDetails.openGroup(groupId);

    await expect(piB.groupDetails.heading).toHaveText(`Collaboration Group: ${groupName}`);
    await expect(piB.groupDetails.memberRow(piA.username)).toContainText("PI");
    await expect(piB.groupDetails.memberRow(piB.username)).toContainText("PI");
  });

  test("As a PI, I can invite another PI to an existing Collaboration Group", async ({ flowSelfServicePi }) => {
    const piA = await flowSelfServicePi();
    const piB = await flowSelfServicePi();
    const piC = await flowSelfServicePi();
    await giveOwnLabGroup(piC, "e2eCollabPiC");

    const { groupId } = await createCollaborationGroup(piA, piB);

    await piA.groupDetails.openGroup(groupId);
    await piA.groupDetails.inviteAnotherPiToCollaboration(piC.username);

    await piC.workspace.open();
    const messages = await piC.workspace.openReceivedMessages();
    await messages.acceptFirstRequest();
    await messages.close();

    await piA.groupDetails.openGroup(groupId);
    await expect(piA.groupDetails.memberRow(piC.username)).toContainText("PI");
  });

  test("As a PI, I can rename a Collaboration Group", async ({ flowSelfServicePi }) => {
    const piA = await flowSelfServicePi();
    const piB = await flowSelfServicePi();
    const { groupId } = await createCollaborationGroup(piA, piB);
    const renamedTo = uniqueName("e2eCollabGroupRenamed");

    await piA.groupDetails.openGroup(groupId);
    await piA.groupDetails.rename(renamedTo);

    await expect(piA.groupDetails.heading).toHaveText(`Collaboration Group: ${renamedTo}`);
  });

  test("As a PI, I can leave a Collaboration Group", async ({ flowSelfServicePi }) => {
    const piA = await flowSelfServicePi();
    const piB = await flowSelfServicePi();
    const { groupId } = await createCollaborationGroup(piA, piB);

    await piB.groupDetails.openGroup(groupId);
    await piB.groupDetails.leaveCollaboration();

    await piA.groupDetails.openGroup(groupId);
    await expect(piA.groupDetails.memberRow(piB.username)).toHaveCount(0);
  });
});
