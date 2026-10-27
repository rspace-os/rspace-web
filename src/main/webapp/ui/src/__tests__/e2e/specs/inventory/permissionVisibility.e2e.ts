import { expect } from "@playwright/test";
import { expectFullAccess, expectPublicAccess } from "@/__tests__/e2e/assertions/inventoryAccess";
import {
  createContainerGroupScenario,
  createInventoryActor,
  createPermissionScenario,
  createSample,
  inventoryPermissionTest as test,
} from "@/__tests__/e2e/fixtures/inventoryPermissions";
import { tags } from "@/__tests__/e2e/tags";

test.describe(`Inventory permissions: visibility`, { tag: [tags.INVENTORY, tags.MOBILE] }, () => {
  test(`As a user, I can access records according to ownership and group membership`, async ({
    openRecordAs,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const { alice, bob, charlie } = await createPermissionScenario(clientSysadmin, apiContext);

    await expectFullAccess(await openRecordAs(alice, "container", alice.container.id, alice.container.name));

    await expectPublicAccess(await openRecordAs(bob, "container", alice.container.id, alice.container.name));

    await expectPublicAccess(await openRecordAs(charlie, "container", alice.container.id, alice.container.name));

    await expectFullAccess(await openRecordAs(charlie, "sample", bob.sample.id, bob.sample.name));
  });

  test(`As a Sample owner, I can see new Samples default to Owner's groups sharing`, async ({
    openRecordAs,
    clientSysadmin,
    apiContext,
  }) => {
    const bob = await test.step("Given Bob (a PI) exists", () =>
      createInventoryActor(clientSysadmin, apiContext, "Bob", "ROLE_PI"));

    const sample = await test.step("When Bob creates a Sample", () => createSample(bob));

    const bobSample = await openRecordAs(bob, "sample", sample.id, sample.name);
    await bobSample.enterEditMode();
    await bobSample.expandSection("Access Permissions");
    await expect(bobSample.accessPermissions().radio("Owner's groups")).toBeChecked();
  });

  test(`As a user, I can see owner-group access change when an item is transferred to a new owner`, async ({
    openRecordAs,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const { alice, bob, charlie } =
      await test.step("Given Alice owns a Container and Bob and Charlie share a lab group", () =>
        createContainerGroupScenario(clientSysadmin, apiContext));

    const aliceContainer = await openRecordAs(alice, "container", alice.container.id, alice.container.name);
    const transferDialog = await aliceContainer.openTransferDialog();
    await transferDialog.selectRecipient(bob.username);
    await transferDialog.confirmTransfer();

    await expectPublicAccess(await openRecordAs(alice, "container", alice.container.id, alice.container.name));

    await expectFullAccess(await openRecordAs(bob, "container", alice.container.id, alice.container.name));

    await expectFullAccess(await openRecordAs(charlie, "container", alice.container.id, alice.container.name));
  });
});
