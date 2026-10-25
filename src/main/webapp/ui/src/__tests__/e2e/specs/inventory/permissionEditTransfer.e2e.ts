import { expect } from "@playwright/test";
import {
  EDIT_ACTIONS,
  expectActionsDisabled,
  expectActionsEnabled,
  expectFullAccess,
} from "@/__tests__/e2e/assertions/inventoryAccess";
import {
  createGroup,
  createInventoryActor,
  createSample,
  createSampleSharedWithGroupmate,
  inventoryPermissionTest as test,
} from "@/__tests__/e2e/fixtures/inventoryPermissions";
import { tags } from "@/__tests__/e2e/tags";
import { alphaNumericUnique } from "@/__tests__/e2e/testData";

test.describe(`Inventory permissions: edit and transfer rights`, { tag: [tags.INVENTORY, tags.MOBILE] }, () => {
  test(`As a shared group's PI, I can view and transfer, but not edit, a member's Only the Owner Sample`, async ({
    openRecordAs,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const bob = await test.step("Given Bob (a PI) exists", () =>
      createInventoryActor(clientSysadmin, apiContext, "Bob", "ROLE_PI"));

    const charlie =
      await test.step("And Charlie (a regular user) shares a lab group with Bob, and owns a Sample", async () => {
        const charlie = await createInventoryActor(clientSysadmin, apiContext, "Charlie", "ROLE_USER");
        await createGroup(clientSysadmin, bob, charlie);
        return { ...charlie, sample: await createSample(charlie) };
      });

    const charlieSample = await openRecordAs(charlie, "sample", charlie.sample.id, charlie.sample.name);
    await charlieSample.enterEditMode();
    await charlieSample.expandSection("Access Permissions");
    await charlieSample.accessPermissions().setSharingMode("Only the Owner");
    await charlieSample.saveEdit();

    const bobSample = await openRecordAs(bob, "sample", charlie.sample.id, charlie.sample.name);
    await expectFullAccess(bobSample);
    // Bob reads as Charlie's PI, not through sharing, so he can't edit; transfer is a PI right regardless.
    await expectActionsDisabled(bobSample, EDIT_ACTIONS);
    await expectActionsEnabled(bobSample, ["Transfer"]);

    const transferDialog = await bobSample.openTransferDialog();
    await transferDialog.selectRecipient(bob.username);
    await transferDialog.confirmTransfer();

    expect((await bob.client.getSample(charlie.sample.id)).owner.username).toBe(bob.username);
  });

  test(`As a groupmate, I can see edit actions but not Transfer on a Sample shared with Owner's groups`, async ({
    openRecordAs,
    clientSysadmin,
    apiContext,
  }) => {
    const { bob, charlie } = await test.step("Given Bob owns a Sample shared with groupmate Charlie", () =>
      createSampleSharedWithGroupmate(clientSysadmin, apiContext));

    const charlieSample = await openRecordAs(charlie, "sample", bob.sample.id, bob.sample.name);
    await expectFullAccess(charlieSample);
    await expectActionsEnabled(charlieSample, EDIT_ACTIONS);
    await expectActionsDisabled(charlieSample, ["Transfer"]);
  });

  test(`As a groupmate, I can rename a Sample shared with Owner's groups`, async ({
    openRecordAs,
    clientSysadmin,
    apiContext,
  }) => {
    test.fixme(
      true,
      "Product defect: edits typed straight after entering Edit mode on a Sample are lost, because SampleModel.fetchAdditionalInfo returns before its refetch lands and the refetch overwrites the form. Remove this fixme once it awaits the fetch.",
    );
    test.slow();

    const { bob, charlie } = await test.step("Given Bob owns a Sample shared with groupmate Charlie", () =>
      createSampleSharedWithGroupmate(clientSysadmin, apiContext));
    const newName = alphaNumericUnique("e2ePermRenamed");

    const charlieSample = await openRecordAs(charlie, "sample", bob.sample.id, bob.sample.name);
    await charlieSample.enterEditMode();
    await charlieSample.rename(newName);
    await charlieSample.saveEdit();

    expect((await bob.client.getSample(bob.sample.id)).name).toBe(newName);
  });
});
