import { DocumentsClient } from "@/__tests__/e2e/api/clients/DocumentsClient";
import { ShareClient } from "@/__tests__/e2e/api/clients/ShareClient";
import {
  expectFullAccess,
  expectPublicAccess,
  expectRestrictedAccess,
} from "@/__tests__/e2e/assertions/inventoryAccess";
import {
  createActor,
  createContainer,
  createGroup,
  createInventoryActor,
  createPermissionScenario,
  storeSubSampleInContainer,
  inventoryPermissionTest as test,
} from "@/__tests__/e2e/fixtures/inventoryPermissions";
import { tags } from "@/__tests__/e2e/tags";
import { alphaNumericUnique } from "@/__tests__/e2e/testData";

test.describe(`Inventory permissions: access through related records`, { tag: [tags.INVENTORY, tags.MOBILE] }, () => {
  test(`As a container owner, I can transitively view a stored record's parent Sample`, async ({
    openRecordAs,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const { alice, bob, charlie, group } = await createPermissionScenario(clientSysadmin, apiContext);
    const subSampleId = bob.sample.subSamples[0].id;

    await storeSubSampleInContainer(alice, alice.container.id, bob, subSampleId, group.id);

    await expectFullAccess(await openRecordAs(charlie, "container", alice.container.id, alice.container.name));

    await expectRestrictedAccess(await openRecordAs(alice, "sample", bob.sample.id, bob.sample.name));

    const tempContainer = await createContainer(bob);
    await bob.client.moveSubSample(subSampleId, {
      parentContainers: [{ id: tempContainer.id }],
      parentLocation: { coordX: 2, coordY: 3 },
    });

    await expectPublicAccess(await openRecordAs(alice, "sample", bob.sample.id, bob.sample.name));

    await expectPublicAccess(await openRecordAs(bob, "container", alice.container.id, alice.container.name));
  });

  test(`As a container owner, I can see restricted details of a stored subsample but not of its sibling stored elsewhere`, async ({
    openRecordAs,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const { alice, bob, group } = await createPermissionScenario(clientSysadmin, apiContext);
    const [storedSubSample, otherSubSample] = bob.sample.subSamples;

    await test.step("And Bob stores one of the Sample's subsamples in Alice's Container", () =>
      storeSubSampleInContainer(alice, alice.container.id, bob, storedSubSample.id, group.id));

    await expectRestrictedAccess(await openRecordAs(alice, "subsample", storedSubSample.id, storedSubSample.name));

    await expectPublicAccess(await openRecordAs(alice, "subsample", otherSubSample.id, otherSubSample.name));
  });

  test(`As a reader of a shared document, I can see restricted details of the materials in its List of Materials`, async ({
    openRecordAs,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const { bob, charlie, group } = await test.step("Given Bob (a PI) shares a lab group with Charlie", async () => {
      const bob = await createInventoryActor(clientSysadmin, apiContext, "Bob", "ROLE_PI");
      const charlie = await createActor(clientSysadmin, "Charlie", "ROLE_USER");
      return { bob, charlie, group: await createGroup(clientSysadmin, bob, charlie) };
    });

    const container = await test.step("And Bob owns a top-level Container shared with no one", async () => {
      const container = await createContainer(bob);
      await bob.client.updateContainer(container.id, { sharingMode: "OWNER_ONLY", sharedWith: [] });
      await bob.client.moveContainerToTopLevel(container.id);
      return container;
    });

    await expectPublicAccess(await openRecordAs(charlie, "container", container.id, container.name));

    await test.step("When Bob lists the Container in a document shared with Charlie's group", async () => {
      const document = await new DocumentsClient(apiContext, bob.apiKey).create({
        name: alphaNumericUnique("e2ePermDoc"),
      });
      await bob.client.createListOfMaterials({
        name: alphaNumericUnique("e2ePermLoM"),
        elnFieldId: document.fields[0].id,
        materials: [{ invRec: { id: container.id, type: "CONTAINER" } }],
      });
      await new ShareClient(apiContext, bob.apiKey).shareWithGroup([document.id], group.id, "READ");
    });

    await expectRestrictedAccess(await openRecordAs(charlie, "container", container.id, container.name));
  });

  test(`As a groupmate, I can see restricted details of an Only the Owner Container on my PI's Bench until it moves to the top level`, async ({
    openRecordAs,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const { bob, charlie, container } =
      await test.step("Given Bob (a PI) has a Container on his Bench and Charlie is in his lab group", async () => {
        const bob = await createInventoryActor(clientSysadmin, apiContext, "Bob", "ROLE_PI");
        const charlie = await createActor(clientSysadmin, "Charlie", "ROLE_USER");
        await createGroup(clientSysadmin, bob, charlie);
        return { bob, charlie, container: await createContainer(bob) };
      });

    const bobContainer = await openRecordAs(bob, "container", container.id, container.name);
    await bobContainer.enterEditMode();
    await bobContainer.expandSection("Access Permissions");
    await bobContainer.accessPermissions().setSharingMode("Only the Owner");
    await bobContainer.saveEdit();

    await expectRestrictedAccess(await openRecordAs(charlie, "container", container.id, container.name));

    await bob.client.moveContainerToTopLevel(container.id);

    await expectPublicAccess(await openRecordAs(charlie, "container", container.id, container.name));
  });
});
