import { expect } from "@playwright/test";
import {
  expectFullAccess,
  expectPublicAccess,
  expectRestrictedAccess,
} from "@/__tests__/e2e/assertions/inventoryAccess";
import {
  createActor,
  createContainerGroupScenario,
  createGroup,
  createSampleSharedWithGroupmate,
  inventoryPermissionTest as test,
} from "@/__tests__/e2e/fixtures/inventoryPermissions";
import { tags } from "@/__tests__/e2e/tags";

test.describe(`Inventory permissions: sharing modes`, { tag: [tags.INVENTORY, tags.MOBILE] }, () => {
  test(`As a Sample owner, I can remove a groupmate's access with Only the Owner after its contents leave my Bench`, async ({
    openRecordAs,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const { bob, charlie } = await test.step("Given Bob owns a Sample shared with groupmate Charlie", () =>
      createSampleSharedWithGroupmate(clientSysadmin, apiContext));

    const bobSample = await openRecordAs(bob, "sample", bob.sample.id, bob.sample.name);
    await bobSample.enterEditMode();
    await bobSample.expandSection("Access Permissions");
    await bobSample.accessPermissions().setSharingMode("Only the Owner");
    await bobSample.saveEdit();

    await expectRestrictedAccess(await openRecordAs(charlie, "sample", bob.sample.id, bob.sample.name));

    for (const subSample of bob.sample.subSamples) {
      await bob.client.deleteSubSample(subSample.id);
    }

    await expectPublicAccess(await openRecordAs(charlie, "sample", bob.sample.id, bob.sample.name));
  });

  test(`As a Sample owner, I can remove a groupmate's access with an empty Explicit access list`, async ({
    openRecordAs,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const { bob, charlie } = await test.step("Given Bob owns a Sample shared with groupmate Charlie", () =>
      createSampleSharedWithGroupmate(clientSysadmin, apiContext));

    const bobSample = await openRecordAs(bob, "sample", bob.sample.id, bob.sample.name);
    await bobSample.enterEditMode();
    await bobSample.expandSection("Access Permissions");
    await bobSample.accessPermissions().setSharingMode("Explicit access list");
    await bobSample.saveEdit();

    const saved = await bob.client.getSample(bob.sample.id);
    expect(saved.sharingMode).toBe("WHITELIST");
    expect(saved.sharedWith?.filter(({ shared }) => shared)).toEqual([]);

    for (const subSample of bob.sample.subSamples) {
      await bob.client.deleteSubSample(subSample.id);
    }

    await expectFullAccess(await openRecordAs(bob, "sample", bob.sample.id, bob.sample.name));

    await expectPublicAccess(await openRecordAs(charlie, "sample", bob.sample.id, bob.sample.name));
  });

  test(`As a Sample owner, I can give another group full access with an Explicit access list`, async ({
    openRecordAs,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const { alice, otherGroup } = await test.step("Given Alice (a PI) runs a lab group of her own", async () => {
      const alice = await createActor(clientSysadmin, "Alice", "ROLE_PI");
      return { alice, otherGroup: await createGroup(clientSysadmin, alice) };
    });

    const { bob, charlie } = await test.step("And Bob owns a Sample shared with groupmate Charlie", () =>
      createSampleSharedWithGroupmate(clientSysadmin, apiContext));

    const bobSample = await openRecordAs(bob, "sample", bob.sample.id, bob.sample.name);
    await bobSample.enterEditMode();
    await bobSample.expandSection("Access Permissions");
    const permissions = bobSample.accessPermissions();
    await permissions.setSharingMode("Explicit access list");
    await permissions.addGroup(otherGroup.name);
    await bobSample.saveEdit();

    for (const subSample of bob.sample.subSamples) {
      await bob.client.deleteSubSample(subSample.id);
    }

    for (const fullAccessUser of [alice, bob]) {
      await expectFullAccess(await openRecordAs(fullAccessUser, "sample", bob.sample.id, bob.sample.name));
    }

    await expectPublicAccess(await openRecordAs(charlie, "sample", bob.sample.id, bob.sample.name));
  });

  test(`As a groupmate, I cannot change access permissions on an item shared with me, and lose access once the owner removes my group`, async ({
    openRecordAs,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const { alice, charlie, group } =
      await test.step("Given Alice owns a Container and Charlie belongs to a lab group", () =>
        createContainerGroupScenario(clientSysadmin, apiContext));

    const aliceContainer = await openRecordAs(alice, "container", alice.container.id, alice.container.name);
    await aliceContainer.enterEditMode();
    await aliceContainer.expandSection("Access Permissions");
    const permissions = aliceContainer.accessPermissions();
    await permissions.setSharingMode("Explicit access list");
    await permissions.addGroup(group.name);
    await aliceContainer.saveEdit();

    const charlieContainer = await openRecordAs(charlie, "container", alice.container.id, alice.container.name);
    await charlieContainer.enterEditMode();
    await charlieContainer.expandSection("Access Permissions");
    const charliePermissions = charlieContainer.accessPermissions();
    await expect(charliePermissions.explanation()).toContainText("Only the item's owner");
    await expect(charliePermissions.radio("Explicit access list")).toBeDisabled();
    await expect(charliePermissions.groupCheckbox(group.name)).toBeDisabled();
    await charlieContainer.saveEdit();

    await expectFullAccess(await openRecordAs(charlie, "container", alice.container.id, alice.container.name));

    const unsharingAliceContainer = await openRecordAs(alice, "container", alice.container.id, alice.container.name);
    await unsharingAliceContainer.enterEditMode();
    await unsharingAliceContainer.expandSection("Access Permissions");
    await unsharingAliceContainer.accessPermissions().groupCheckbox(group.name).uncheck();
    await unsharingAliceContainer.saveEdit();

    await expectPublicAccess(await openRecordAs(charlie, "container", alice.container.id, alice.container.name));
  });
});
