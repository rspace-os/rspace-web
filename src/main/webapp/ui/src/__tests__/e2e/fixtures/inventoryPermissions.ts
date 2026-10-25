import { type APIRequestContext, expect } from "@playwright/test";
import { InventoryClient } from "@/__tests__/e2e/api/clients/InventoryClient";
import type { SysadminClient } from "@/__tests__/e2e/api/clients/SysadminClient";
import type { InventoryDetailsPanel } from "@/__tests__/e2e/components/inventory/InventoryDetailsPanel";
import { createDynamicUser, type DynamicUser } from "@/__tests__/e2e/createDynamicUser";
import type { InventoryRecordPathType } from "@/__tests__/e2e/pageObjects/inventory/InventoryPage";
import { alphaNumericUnique } from "@/__tests__/e2e/testData";
import { test } from "./flows";

export type Actor = DynamicUser;
export type InventoryActor = Actor & { client: InventoryClient };

export const inventoryPermissionTest = test.extend<{
  /** Opens a record's page as `user` in a fresh browser context, closed at teardown. */
  openRecordAs: (
    user: Actor,
    type: InventoryRecordPathType,
    id: number,
    recordName: string,
  ) => Promise<InventoryDetailsPanel>;
}>({
  openRecordAs: async ({ pageInventoryForUser }, use) => {
    await use(async (user, type, id, recordName) => {
      const inventory = await pageInventoryForUser(user);
      await inventory.openRecordById(type, id);
      await expect(inventory.detailsPanel.headingNamed(recordName)).toBeVisible();
      return inventory.detailsPanel;
    });
  },
});

export async function createActor(
  clientSysadmin: SysadminClient,
  firstName: string,
  role: "ROLE_PI" | "ROLE_USER",
): Promise<Actor> {
  return createDynamicUser(clientSysadmin, role, `e2ePerm${firstName}`, firstName);
}

export async function createInventoryActor(
  clientSysadmin: SysadminClient,
  apiContext: APIRequestContext,
  firstName: string,
  role: "ROLE_PI" | "ROLE_USER",
): Promise<InventoryActor> {
  const actor = await createActor(clientSysadmin, firstName, role);
  return { ...actor, client: new InventoryClient(apiContext, actor.apiKey) };
}

export async function createContainer(owner: InventoryActor) {
  return owner.client.createContainer({
    name: alphaNumericUnique("e2ePermContainer"),
    cType: "GRID",
    gridLayout: { columnsNumber: 3, rowsNumber: 3 },
  });
}

export async function createSample(owner: InventoryActor, subSampleCount?: number) {
  return owner.client.createSample({
    name: alphaNumericUnique("e2ePermSample"),
    newSampleSubSamplesCount: subSampleCount,
  });
}

export async function createGroup(clientSysadmin: SysadminClient, pi: Actor, ...members: Actor[]) {
  return clientSysadmin.createGroup({
    displayName: alphaNumericUnique("e2ePermGroup"),
    type: "LAB_GROUP",
    users: [
      { username: pi.username, roleInGroup: "PI" },
      ...members.map(({ username }) => ({ username, roleInGroup: "DEFAULT" as const })),
    ],
  });
}

export async function createSampleSharedWithGroupmate(clientSysadmin: SysadminClient, apiContext: APIRequestContext) {
  const bob = await createInventoryActor(clientSysadmin, apiContext, "Bob", "ROLE_PI");
  const charlie = await createActor(clientSysadmin, "Charlie", "ROLE_USER");
  await createGroup(clientSysadmin, bob, charlie);
  return { bob: { ...bob, sample: await createSample(bob, 2) }, charlie };
}

/** Bob can only store into Alice's Container while it's shared with his group, so share it just for the move. */
export async function storeSubSampleInContainer(
  containerOwner: InventoryActor,
  containerId: number,
  subSampleOwner: InventoryActor,
  subSampleId: number,
  groupId: number,
) {
  await containerOwner.client.updateContainer(containerId, {
    sharingMode: "WHITELIST",
    sharedWith: [{ group: { id: groupId }, shared: true }],
  });
  await subSampleOwner.client.moveSubSample(subSampleId, {
    parentContainers: [{ id: containerId }],
    parentLocation: { coordX: 2, coordY: 3 },
  });
  await containerOwner.client.updateContainer(containerId, {
    sharingMode: "WHITELIST",
    sharedWith: [{ group: { id: groupId }, shared: false }],
  });
}

export async function createPermissionScenario(clientSysadmin: SysadminClient, apiContext: APIRequestContext) {
  const alice = await test.step("Given Alice (a PI with no shared group) owns a Container", async () => {
    const actor = await createInventoryActor(clientSysadmin, apiContext, "Alice", "ROLE_PI");
    return { ...actor, container: await createContainer(actor) };
  });

  const bob = await test.step("And Bob (a PI) exists", () =>
    createInventoryActor(clientSysadmin, apiContext, "Bob", "ROLE_PI"));

  const { charlie, group } = await test.step("And Charlie (a regular user) shares a lab group with Bob", async () => {
    const charlie = await createActor(clientSysadmin, "Charlie", "ROLE_USER");
    return { charlie, group: await createGroup(clientSysadmin, bob, charlie) };
  });

  const sample =
    await test.step("And Bob owns a Sample (created after joining the group, so it's shared with it)", () =>
      createSample(bob, 2));

  return { alice, bob: { ...bob, sample }, charlie, group };
}

export async function createContainerGroupScenario(clientSysadmin: SysadminClient, apiContext: APIRequestContext) {
  const alice = await createInventoryActor(clientSysadmin, apiContext, "Alice", "ROLE_PI");
  const bob = await createActor(clientSysadmin, "Bob", "ROLE_PI");
  const charlie = await createActor(clientSysadmin, "Charlie", "ROLE_USER");
  const group = await createGroup(clientSysadmin, bob, charlie);
  return { alice: { ...alice, container: await createContainer(alice) }, bob, charlie, group };
}
