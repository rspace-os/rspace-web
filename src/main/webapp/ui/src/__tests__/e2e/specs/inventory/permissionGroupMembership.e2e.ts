import { expectFullAccess, expectPublicAccess } from "@/__tests__/e2e/assertions/inventoryAccess";
import {
  createActor,
  createContainer,
  createGroup,
  createInventoryActor,
  type InventoryActor,
  inventoryPermissionTest as test,
} from "@/__tests__/e2e/fixtures/inventoryPermissions";
import { tags } from "@/__tests__/e2e/tags";
import { alphaNumericUnique } from "@/__tests__/e2e/testData";

/** Alice's Container, shared with the group through an Explicit access list. */
async function createContainerSharedWithGroup(alice: InventoryActor, groupId: number) {
  const container = await createContainer(alice);
  await alice.client.updateContainer(container.id, {
    sharingMode: "WHITELIST",
    sharedWith: [{ group: { id: groupId }, shared: true }],
  });
  return container;
}

test.describe(`Inventory permissions: group membership`, { tag: [tags.INVENTORY, tags.MOBILE] }, () => {
  test(`As a group member, I lose access to items shared with my group once its PI removes me`, async ({
    openRecordAs,
    flowUserSession,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const { bob, charlie, group } = await test.step("Given Charlie is in Bob's lab group", async () => {
      const bob = await createInventoryActor(clientSysadmin, apiContext, "Bob", "ROLE_PI");
      const charlie = await createActor(clientSysadmin, "Charlie", "ROLE_USER");
      return { bob, charlie, group: await createGroup(clientSysadmin, bob, charlie) };
    });

    const records = await test.step("And Bob and Alice each own a Container shared with the group", async () => {
      const alice = await createInventoryActor(clientSysadmin, apiContext, "Alice", "ROLE_PI");
      return [await createContainer(bob), await createContainerSharedWithGroup(alice, group.id)];
    });

    for (const { id, name } of records) {
      await expectFullAccess(await openRecordAs(charlie, "container", id, name));
    }

    const bobSession = await flowUserSession(bob.username, bob.password);
    await bobSession.groupDetails.openGroup(group.id);
    await bobSession.groupDetails.removeMember(charlie.username);

    for (const { id, name } of records) {
      await expectPublicAccess(await openRecordAs(charlie, "container", id, name));
    }
  });

  test(`As a Project Group member, I lose access to items shared with the group once I leave it`, async ({
    openRecordAs,
    flowUserSession,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const { bob, charlie, group } = await test.step("Given Charlie is in Bob's Project Group", async () => {
      const bob = await createInventoryActor(clientSysadmin, apiContext, "Bob", "ROLE_USER");
      const charlie = await createActor(clientSysadmin, "Charlie", "ROLE_USER");
      const group = await clientSysadmin.createGroup({
        displayName: alphaNumericUnique("e2ePermProjectGroup"),
        type: "PROJECT_GROUP",
        users: [
          { username: bob.username, roleInGroup: "GROUP_OWNER" },
          { username: charlie.username, roleInGroup: "DEFAULT" },
        ],
      });
      return { bob, charlie, group };
    });

    const records = await test.step("And Bob and Alice each own a Container shared with the group", async () => {
      const alice = await createInventoryActor(clientSysadmin, apiContext, "Alice", "ROLE_PI");
      return [await createContainer(bob), await createContainerSharedWithGroup(alice, group.id)];
    });

    for (const { id, name } of records) {
      await expectFullAccess(await openRecordAs(charlie, "container", id, name));
    }

    const charlieSession = await flowUserSession(charlie.username, charlie.password);
    await charlieSession.groupDetails.openGroup(group.id);
    await charlieSession.groupDetails.leaveGroup();

    for (const { id, name } of records) {
      await expectPublicAccess(await openRecordAs(charlie, "container", id, name));
    }
  });
});
