import { type APIRequestContext, type Browser, type BrowserContextOptions, expect } from "@playwright/test";
import { InventoryClient } from "@/__tests__/e2e/api/clients/InventoryClient";
import type { SysadminClient } from "@/__tests__/e2e/api/clients/SysadminClient";
import { InventoryDetailsPanel } from "@/__tests__/e2e/components/inventory/InventoryDetailsPanel";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { LoginPage } from "@/__tests__/e2e/pageObjects/auth/LoginPage";
import { tags } from "@/__tests__/e2e/tags";
import { alphaNumericUnique } from "@/__tests__/e2e/testData";

const PASSWORD = "Passw0rd!23";
const openContexts = new Set<Awaited<ReturnType<Browser["newContext"]>>>();

type Actor = { username: string; password: string };
type InventoryActor = Actor & { client: InventoryClient };

test.afterEach(async () => {
  await Promise.all([...openContexts].map((context) => context.close()));
  openContexts.clear();
});

async function createActor(
  clientSysadmin: SysadminClient,
  firstName: string,
  role: "ROLE_PI" | "ROLE_USER",
  apiKey?: string,
): Promise<Actor> {
  const username = alphaNumericUnique(`e2ePerm${firstName}`);
  await clientSysadmin.createUser({
    username,
    password: PASSWORD,
    email: `${username}@example.com`,
    firstName,
    lastName: "Perm",
    role,
    apiKey,
  });
  return { username, password: PASSWORD };
}

async function createInventoryActor(
  clientSysadmin: SysadminClient,
  apiContext: APIRequestContext,
  firstName: string,
  role: "ROLE_PI" | "ROLE_USER",
): Promise<InventoryActor> {
  const apiKey = alphaNumericUnique(`e2ePerm${firstName}Key`).slice(0, 32);
  return {
    ...(await createActor(clientSysadmin, firstName, role, apiKey)),
    client: new InventoryClient(apiContext, apiKey),
  };
}

async function createContainer(owner: InventoryActor) {
  return owner.client.createContainer({
    name: alphaNumericUnique("e2ePermContainer"),
    cType: "GRID",
    gridLayout: { columnsNumber: 3, rowsNumber: 3 },
  });
}

async function createSample(owner: InventoryActor, subSampleCount?: number) {
  return owner.client.createSample({
    name: alphaNumericUnique("e2ePermSample"),
    newSampleSubSamplesCount: subSampleCount,
  });
}

async function createGroup(clientSysadmin: SysadminClient, pi: Actor, member: Actor) {
  return clientSysadmin.createGroup({
    displayName: alphaNumericUnique("e2ePermGroup"),
    type: "LAB_GROUP",
    users: [
      { username: pi.username, roleInGroup: "PI" },
      { username: member.username, roleInGroup: "DEFAULT" },
    ],
  });
}

async function createPermissionScenario(clientSysadmin: SysadminClient, apiContext: APIRequestContext) {
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

async function createContainerGroupScenario(clientSysadmin: SysadminClient, apiContext: APIRequestContext) {
  const alice = await createInventoryActor(clientSysadmin, apiContext, "Alice", "ROLE_PI");
  const bob = await createActor(clientSysadmin, "Bob", "ROLE_PI");
  const charlie = await createActor(clientSysadmin, "Charlie", "ROLE_USER");
  const group = await createGroup(clientSysadmin, bob, charlie);
  return { alice: { ...alice, container: await createContainer(alice) }, bob, charlie, group };
}

async function openRecordAsUser(
  browser: Browser,
  browserContextOptions: BrowserContextOptions,
  user: Actor,
  path: string,
  recordName: string,
) {
  const ctx = await browser.newContext({ ...browserContextOptions, storageState: undefined });
  openContexts.add(ctx);
  ctx.once("close", () => openContexts.delete(ctx));
  try {
    const page = await ctx.newPage();
    const loginPage = new LoginPage(page);
    await loginPage.open();
    await loginPage.login(user.username, user.password);
    await page.waitForURL((url) => url.pathname === "/workspace");
    await page.goto(path);
    const detailsPanel = new InventoryDetailsPanel(page);
    await expect(detailsPanel.heading).toContainText(recordName);
    return { detailsPanel, close: () => ctx.close() };
  } catch (error) {
    await ctx.close();
    throw error;
  }
}

async function expectFullAccess(detailsPanel: InventoryDetailsPanel) {
  await expect(detailsPanel.permissionAlert).toHaveCount(0);
  await expect(detailsPanel.section("Barcodes")).toBeVisible();
  await expect(detailsPanel.section("Attachments")).toBeVisible();
  await expect(await detailsPanel.duplicateControl()).toBeEnabled();
  await expect(await detailsPanel.moreActionItem("Add to Basket")).toBeEnabled();
}

async function expectPublicAccess(detailsPanel: InventoryDetailsPanel) {
  await expect(detailsPanel.permissionAlert).toContainText("You do not have permission to see any of the details");
  await expect(detailsPanel.section("Barcodes")).toHaveCount(0);
  await expect(detailsPanel.section("Attachments")).toHaveCount(0);
  await expect(await detailsPanel.duplicateControl()).toBeDisabled();
  await expect(await detailsPanel.moreActionItem("Add to Basket")).toBeEnabled();
}

async function expectRestrictedAccess(detailsPanel: InventoryDetailsPanel) {
  await expect(detailsPanel.permissionAlert).toContainText("You do not have permission to see all of the details");
  await expect(detailsPanel.section("Barcodes")).toBeVisible();
  await expect(detailsPanel.section("Attachments")).toHaveCount(0);
  await expect(await detailsPanel.duplicateControl()).toBeDisabled();
  await expect(await detailsPanel.moreActionItem("Add to Basket")).toBeEnabled();
}

test.describe(`Inventory permissions`, { tag: [tags.INVENTORY, tags.MOBILE] }, () => {
  test(`As a user, I can access records according to ownership and group membership`, async ({
    browser,
    browserContextOptions,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const { alice, bob, charlie } = await createPermissionScenario(clientSysadmin, apiContext);

    const aliceContainer = await openRecordAsUser(
      browser,
      browserContextOptions,
      alice,
      `/inventory/container/${alice.container.id}`,
      alice.container.name,
    );
    await expectFullAccess(aliceContainer.detailsPanel);
    await aliceContainer.close();

    const bobContainer = await openRecordAsUser(
      browser,
      browserContextOptions,
      bob,
      `/inventory/container/${alice.container.id}`,
      alice.container.name,
    );
    await expectPublicAccess(bobContainer.detailsPanel);
    await bobContainer.close();

    const charlieContainer = await openRecordAsUser(
      browser,
      browserContextOptions,
      charlie,
      `/inventory/container/${alice.container.id}`,
      alice.container.name,
    );
    await expectPublicAccess(charlieContainer.detailsPanel);
    await charlieContainer.close();

    const charlieSample = await openRecordAsUser(
      browser,
      browserContextOptions,
      charlie,
      `/inventory/sample/${bob.sample.id}`,
      bob.sample.name,
    );
    await expectFullAccess(charlieSample.detailsPanel);
    await charlieSample.close();
  });

  test(`As a container owner, I can transitively view a stored record's parent Sample`, async ({
    browser,
    browserContextOptions,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const { alice, bob, charlie, group } = await createPermissionScenario(clientSysadmin, apiContext);
    const subSampleId = bob.sample.subSamples[0].id;

    await alice.client.updateContainer(alice.container.id, {
      sharingMode: "WHITELIST",
      sharedWith: [{ group: { id: group.id }, shared: true }],
    });
    await bob.client.moveSubSample(subSampleId, {
      parentContainers: [{ id: alice.container.id }],
      parentLocation: { coordX: 2, coordY: 3 },
    });

    await alice.client.updateContainer(alice.container.id, {
      sharingMode: "WHITELIST",
      sharedWith: [{ group: { id: group.id }, shared: false }],
    });

    const charlieContainer = await openRecordAsUser(
      browser,
      browserContextOptions,
      charlie,
      `/inventory/container/${alice.container.id}`,
      alice.container.name,
    );
    await expectFullAccess(charlieContainer.detailsPanel);
    await charlieContainer.close();

    const aliceSampleInContainer = await openRecordAsUser(
      browser,
      browserContextOptions,
      alice,
      `/inventory/sample/${bob.sample.id}`,
      bob.sample.name,
    );
    await expectRestrictedAccess(aliceSampleInContainer.detailsPanel);
    await aliceSampleInContainer.close();

    const tempContainer = await createContainer(bob);
    await bob.client.moveSubSample(subSampleId, {
      parentContainers: [{ id: tempContainer.id }],
      parentLocation: { coordX: 2, coordY: 3 },
    });

    const aliceSampleOutsideContainer = await openRecordAsUser(
      browser,
      browserContextOptions,
      alice,
      `/inventory/sample/${bob.sample.id}`,
      bob.sample.name,
    );
    await expectPublicAccess(aliceSampleOutsideContainer.detailsPanel);
    await aliceSampleOutsideContainer.close();

    const bobContainer = await openRecordAsUser(
      browser,
      browserContextOptions,
      bob,
      `/inventory/container/${alice.container.id}`,
      alice.container.name,
    );
    await expectPublicAccess(bobContainer.detailsPanel);
    await bobContainer.close();
  });
  test(`As a Sample owner, I can see new Samples default to Owner's groups sharing`, async ({
    browser,
    browserContextOptions,
    clientSysadmin,
    apiContext,
  }) => {
    const bob = await test.step("Given Bob (a PI) exists", () =>
      createInventoryActor(clientSysadmin, apiContext, "Bob", "ROLE_PI"));

    const sample = await test.step("When Bob creates a Sample", () => createSample(bob));

    const bobSample = await openRecordAsUser(
      browser,
      browserContextOptions,
      bob,
      `/inventory/sample/${sample.id}`,
      sample.name,
    );
    await bobSample.detailsPanel.enterEditMode();
    await bobSample.detailsPanel.expandSection("Access Permissions");
    await expect(bobSample.detailsPanel.accessPermissions().radio("Owner's groups")).toBeChecked();
    await bobSample.close();
  });

  test(`As a Sample owner, I can remove a groupmate's access with Only the Owner after its contents leave my Bench`, async ({
    browser,
    browserContextOptions,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const { bob, charlie } = await test.step("Given Bob owns a Sample shared with groupmate Charlie", async () => {
      const bob = await createInventoryActor(clientSysadmin, apiContext, "Bob", "ROLE_PI");
      const charlie = await createActor(clientSysadmin, "Charlie", "ROLE_USER");
      await createGroup(clientSysadmin, bob, charlie);
      return { bob: { ...bob, sample: await createSample(bob, 2) }, charlie };
    });

    const bobSample = await openRecordAsUser(
      browser,
      browserContextOptions,
      bob,
      `/inventory/sample/${bob.sample.id}`,
      bob.sample.name,
    );
    await bobSample.detailsPanel.enterEditMode();
    await bobSample.detailsPanel.expandSection("Access Permissions");
    await bobSample.detailsPanel.accessPermissions().setSharingMode("Only the Owner");
    await bobSample.detailsPanel.saveEdit();
    await bobSample.close();

    const restrictedCharlieSample = await openRecordAsUser(
      browser,
      browserContextOptions,
      charlie,
      `/inventory/sample/${bob.sample.id}`,
      bob.sample.name,
    );
    await expectRestrictedAccess(restrictedCharlieSample.detailsPanel);
    await restrictedCharlieSample.close();

    for (const subSample of bob.sample.subSamples) {
      await bob.client.deleteSubSample(subSample.id);
    }

    const publicCharlieSample = await openRecordAsUser(
      browser,
      browserContextOptions,
      charlie,
      `/inventory/sample/${bob.sample.id}`,
      bob.sample.name,
    );
    await expectPublicAccess(publicCharlieSample.detailsPanel);
    await publicCharlieSample.close();
  });

  test(`As a shared group's PI, I can retain full access to records set to Only the Owner`, async ({
    browser,
    browserContextOptions,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const bob = await test.step("Given Bob (a PI) exists", () => createActor(clientSysadmin, "Bob", "ROLE_PI"));

    const charlie =
      await test.step("And Charlie (a regular user) shares a lab group with Bob, and owns a Sample", async () => {
        const charlie = await createInventoryActor(clientSysadmin, apiContext, "Charlie", "ROLE_USER");
        await createGroup(clientSysadmin, bob, charlie);
        return { ...charlie, sample: await createSample(charlie) };
      });

    const charlieSample = await openRecordAsUser(
      browser,
      browserContextOptions,
      charlie,
      `/inventory/sample/${charlie.sample.id}`,
      charlie.sample.name,
    );
    await charlieSample.detailsPanel.enterEditMode();
    await charlieSample.detailsPanel.expandSection("Access Permissions");
    await charlieSample.detailsPanel.accessPermissions().setSharingMode("Only the Owner");
    await charlieSample.detailsPanel.saveEdit();
    await charlieSample.close();

    const bobSample = await openRecordAsUser(
      browser,
      browserContextOptions,
      bob,
      `/inventory/sample/${charlie.sample.id}`,
      charlie.sample.name,
    );
    await expectFullAccess(bobSample.detailsPanel);
    await bobSample.close();
  });

  test(`As a groupmate, I can remove myself from an Explicit access list and immediately lose access`, async ({
    browser,
    browserContextOptions,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const { alice, charlie, group } =
      await test.step("Given Alice owns a Container and Charlie belongs to a lab group", () =>
        createContainerGroupScenario(clientSysadmin, apiContext));

    const aliceContainer = await openRecordAsUser(
      browser,
      browserContextOptions,
      alice,
      `/inventory/container/${alice.container.id}`,
      alice.container.name,
    );
    await aliceContainer.detailsPanel.enterEditMode();
    await aliceContainer.detailsPanel.expandSection("Access Permissions");
    const permissions = aliceContainer.detailsPanel.accessPermissions();
    await permissions.setSharingMode("Explicit access list");
    await permissions.addGroup(group.name);
    await aliceContainer.detailsPanel.saveEdit();
    await aliceContainer.close();

    const sharedCharlieContainer = await openRecordAsUser(
      browser,
      browserContextOptions,
      charlie,
      `/inventory/container/${alice.container.id}`,
      alice.container.name,
    );
    await expectFullAccess(sharedCharlieContainer.detailsPanel);
    await sharedCharlieContainer.close();

    const editableCharlieContainer = await openRecordAsUser(
      browser,
      browserContextOptions,
      charlie,
      `/inventory/container/${alice.container.id}`,
      alice.container.name,
    );
    await editableCharlieContainer.detailsPanel.enterEditMode();
    await editableCharlieContainer.detailsPanel.expandSection("Access Permissions");
    await editableCharlieContainer.detailsPanel.accessPermissions().groupCheckbox(group.name).uncheck();
    await editableCharlieContainer.detailsPanel.saveEdit();
    await editableCharlieContainer.close();

    const unsharedCharlieContainer = await openRecordAsUser(
      browser,
      browserContextOptions,
      charlie,
      `/inventory/container/${alice.container.id}`,
      alice.container.name,
    );
    await expectPublicAccess(unsharedCharlieContainer.detailsPanel);
    await unsharedCharlieContainer.close();
  });

  test(`As a user, I can see owner-group access change when an item is transferred to a new owner`, async ({
    browser,
    browserContextOptions,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const { alice, bob, charlie } =
      await test.step("Given Alice owns a Container and Bob and Charlie share a lab group", () =>
        createContainerGroupScenario(clientSysadmin, apiContext));

    const aliceContainer = await openRecordAsUser(
      browser,
      browserContextOptions,
      alice,
      `/inventory/container/${alice.container.id}`,
      alice.container.name,
    );
    const transferDialog = await aliceContainer.detailsPanel.openTransferDialog();
    await transferDialog.selectRecipient(bob.username);
    await transferDialog.confirmTransfer();
    await aliceContainer.close();

    const formerOwnerContainer = await openRecordAsUser(
      browser,
      browserContextOptions,
      alice,
      `/inventory/container/${alice.container.id}`,
      alice.container.name,
    );
    await expectPublicAccess(formerOwnerContainer.detailsPanel);
    await formerOwnerContainer.close();

    const newOwnerContainer = await openRecordAsUser(
      browser,
      browserContextOptions,
      bob,
      `/inventory/container/${alice.container.id}`,
      alice.container.name,
    );
    await expectFullAccess(newOwnerContainer.detailsPanel);
    await newOwnerContainer.close();

    const groupmateContainer = await openRecordAsUser(
      browser,
      browserContextOptions,
      charlie,
      `/inventory/container/${alice.container.id}`,
      alice.container.name,
    );
    await expectFullAccess(groupmateContainer.detailsPanel);
    await groupmateContainer.close();
  });
});
