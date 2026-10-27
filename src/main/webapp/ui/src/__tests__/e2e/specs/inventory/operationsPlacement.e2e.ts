import type { APIRequestContext } from "@playwright/test";
import { expect } from "@playwright/test";
import { InventoryClient } from "@/__tests__/e2e/api/clients/InventoryClient";
import type { SysadminClient } from "@/__tests__/e2e/api/clients/SysadminClient";
import type { ApiInventoryGridLocation } from "@/__tests__/e2e/api/models/inventory";
import type { ProcessWizardComponent } from "@/__tests__/e2e/components/inventory/ProcessWizardComponent";
import { ToastsComponent } from "@/__tests__/e2e/components/shared/ToastsComponent";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { InventoryPage } from "@/__tests__/e2e/pageObjects/inventory/InventoryPage";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";
import type { AppUser } from "@/__tests__/e2e/users";

// Remembered process values are per account and change where the wizard opens, so every test here
// runs as a fresh dynamic user.

const ML = 3;

function label({ coordX, coordY }: ApiInventoryGridLocation): string {
  return `${String.fromCharCode(64 + coordY)}${coordX}`;
}

async function origin(client: InventoryClient, subSampleCount = 1) {
  return client.createSample({
    name: uniqueName("e2e-place"),
    newSampleSubSamplesCount: subSampleCount,
    quantity: { numericValue: 10, unitId: ML },
  });
}

async function listContainer(client: InventoryClient, prefix = "e2e-shelf") {
  return client.createContainer({ name: uniqueName(prefix), cType: "LIST" });
}

async function gridContainer(client: InventoryClient, rows: number, columns: number) {
  return client.createContainer({
    name: uniqueName("e2e-box"),
    cType: "GRID",
    gridLayout: { columnsNumber: columns, rowsNumber: rows },
  });
}

async function openWizard(pageInventory: InventoryPage, sampleName: string, subSampleNames: string[]) {
  await pageInventory.openSearch("SUBSAMPLE");
  await pageInventory.searchPanel.search(sampleName);
  for (const name of subSampleNames) {
    await pageInventory.searchPanel.selectItem(name);
  }
  return pageInventory.searchPanel.batchActions.openProcessWizard();
}

async function aliquotToLocation(
  pageInventory: InventoryPage,
  sample: { name: string; subSamples: Array<{ name: string }> },
  count?: number,
): Promise<ProcessWizardComponent> {
  const wizard = await openWizard(pageInventory, sample.name, [sample.subSamples[0].name]);
  await wizard.chooseOperation("Aliquot");
  await wizard.goToLocation({ count });
  return wizard;
}

/** Where the subsamples in a grid container sit, polled since placement follows creation. */
async function placedLabels(client: InventoryClient, containerId: number): Promise<string[]> {
  const container = await client.getContainer(containerId);
  return (container.locations ?? [])
    .filter((l) => l.content)
    .map(label)
    .sort();
}

async function createdSubSampleCount(client: InventoryClient, sampleName: string): Promise<number> {
  return (await client.searchByName(`${sampleName} aliquot`, "SUBSAMPLE")).records.length;
}

test.describe("Inventory operation placement", { tag: tags.INVENTORY }, () => {
  test.describe("on a phone too", { tag: tags.MOBILE }, () => {
    test("As a user, I can aliquot two subsamples into chosen locations of a grid container", async ({
      flowOperationsEnabled: _,
      pageInventory,
      clientInventory,
    }) => {
      const sample = await origin(clientInventory);
      const box = await gridContainer(clientInventory, 2, 2);
      const wizard = await aliquotToLocation(pageInventory, sample, 2);
      const { location } = wizard;

      await location.pick(box.name);
      await expect(location.status).toHaveText(`Select 2 more locations in ${box.name}.`);
      await location.grid.click("A1");
      await expect(location.status).toHaveText(`Select 1 more location in ${box.name}.`);
      await location.grid.click("B2");
      await expect(location.status).toHaveText(`The new subsamples will be placed in ${box.name}.`);
      await wizard.next();
      expect(await wizard.confirmedLocation()).toBe(`${box.name}: A1, B2`);
      await wizard.perform();

      await expect.poll(() => placedLabels(clientInventory, box.id)).toEqual(["A1", "B2"]);
    });

    test("As a user, I can aliquot into a list container", async ({
      flowOperationsEnabled: _,
      pageInventory,
      clientInventory,
    }) => {
      const sample = await origin(clientInventory);
      const shelf = await listContainer(clientInventory);
      const wizard = await aliquotToLocation(pageInventory, sample);

      await wizard.location.pick(shelf.name);

      await expect(wizard.location.placingIn).toHaveText(`Placing in ${shelf.name} (${shelf.globalId})`);
      await expect(wizard.location.changeContainerButton).toBeVisible();
      await expect(wizard.location.status).toHaveText(`The new subsamples will be placed in ${shelf.name}.`);
      await wizard.next();
      expect(await wizard.confirmedLocation()).toBe(shelf.name);
      await wizard.perform();

      await expect
        .poll(async () => (await clientInventory.searchByParent(shelf.globalId, "SUBSAMPLE")).records.length)
        .toBe(1);
      const originAfter = await clientInventory.getSubSample(sample.subSamples[0].id);
      expect(originAfter.parentContainers?.[0].cType).toBe("WORKBENCH");
    });
  });

  test("As a user, a grid container without enough free locations is refused", async ({
    flowOperationsEnabled: _,
    pageInventory,
    clientInventory,
  }) => {
    const sample = await origin(clientInventory);
    const tube = await gridContainer(clientInventory, 1, 1);
    const wizard = await aliquotToLocation(pageInventory, sample, 2);

    await wizard.location.pickContainer(tube.name);

    await expect(wizard.location.deadEnd).toHaveText(
      "Only 1 of the 2 locations needed are free. Choose another container.",
    );
    await expect(wizard.location.placingIn).toBeHidden();
    await expect(wizard.location.searchInput).toBeVisible();
    await expect(wizard.nextButton).toBeDisabled();
    await wizard.location.chooseWorkbench();
    await expect(wizard.nextButton).toBeEnabled();
  });

  test("As a user, lowering the count drops the last chosen locations", async ({
    flowOperationsEnabled: _,
    pageInventory,
    clientInventory,
  }) => {
    const sample = await origin(clientInventory);
    const box = await gridContainer(clientInventory, 2, 2);
    const wizard = await aliquotToLocation(pageInventory, sample, 3);
    await wizard.location.pick(box.name);
    await wizard.location.grid.click("A1", "B1", "B2");
    await expect(wizard.location.status).toHaveText(`The new subsamples will be placed in ${box.name}.`);

    await wizard.back();
    await wizard.back();
    await wizard.expectStep("Amounts");
    await wizard.setCount(2);
    await wizard.next();
    await wizard.next();

    await wizard.location.grid.expectSelected(["A1", "B1"]);
    await expect(wizard.location.status).toHaveText(`The new subsamples will be placed in ${box.name}.`);
    await wizard.next();
    expect(await wizard.confirmedLocation()).toBe(`${box.name}: A1, B1`);
  });

  test("As a user, cancelling leaves nothing behind and nothing remembered", async ({
    flowOperationsEnabled: _,
    pageInventory,
    clientInventory,
  }) => {
    const sample = await origin(clientInventory);
    const box = await gridContainer(clientInventory, 2, 2);
    const wizard = await aliquotToLocation(pageInventory, sample, 2);
    await wizard.location.pick(box.name);
    await wizard.location.grid.click("A1", "B2");

    await wizard.cancel();

    expect(await createdSubSampleCount(clientInventory, sample.name)).toBe(0);
    expect(await placedLabels(clientInventory, box.id)).toEqual([]);
    const reopened = await openWizard(pageInventory, sample.name, [sample.subSamples[0].name]);
    await reopened.chooseOperation("Aliquot");
    await reopened.expectStep("Details");
  });

  test("As a user placing one subsample, a second click moves my pick", async ({
    flowOperationsEnabled: _,
    pageInventory,
    clientInventory,
  }) => {
    const sample = await origin(clientInventory);
    const box = await gridContainer(clientInventory, 2, 2);
    const wizard = await aliquotToLocation(pageInventory, sample, 1);
    await wizard.location.pick(box.name);

    await wizard.location.grid.click("A1", "B2");

    await wizard.location.grid.expectSelected(["B2"]);
    await expect(wizard.location.status).toHaveText(`The new subsamples will be placed in ${box.name}.`);
    await wizard.next();
    expect(await wizard.confirmedLocation()).toBe(`${box.name}: B2`);
  });

  test("As a user, I am told when the location I chose was taken before the subsamples were placed", async ({
    flowOperationsEnabled: _,
    pageInventory,
    clientInventory,
    componentToasts,
  }) => {
    const sample = await origin(clientInventory);
    const box = await gridContainer(clientInventory, 2, 2);
    const wizard = await aliquotToLocation(pageInventory, sample, 1);
    await wizard.location.pick(box.name);
    await wizard.location.grid.click("A1");
    await wizard.next();
    const other = await origin(clientInventory);
    await clientInventory.moveSubSample(other.subSamples[0].id, {
      parentContainers: [{ id: box.id }],
      parentLocation: { coordX: 1, coordY: 1 },
    });

    await wizard.perform();

    await expect(
      componentToasts.byVariant(
        "warning",
        `The new subsamples were created on your workbench, but could not be placed in ${box.name}.`,
      ),
    ).toBeVisible();
    const created = await clientInventory.searchByName(`${sample.name} aliquot`, "SUBSAMPLE");
    expect(created.records).toHaveLength(1);
    const placed = await clientInventory.getSubSample(created.records[0].id);
    expect(placed.parentContainers?.[0].cType).toBe("WORKBENCH");
    expect(await placedLabels(clientInventory, box.id)).toEqual(["A1"]);
  });

  test("As a user, an image container is refused", async ({
    flowOperationsEnabled: _,
    pageInventory,
    clientInventory,
  }) => {
    const sample = await origin(clientInventory);
    const visual = await clientInventory.createContainer({ name: uniqueName("e2e-visual"), cType: "IMAGE" });
    const wizard = await aliquotToLocation(pageInventory, sample);

    await wizard.location.pickContainer(visual.name);

    await expect(wizard.location.deadEnd).toHaveText(
      "Image containers are not supported yet. Choose a grid or list container.",
    );
    await expect(wizard.nextButton).toBeDisabled();
  });

  test("As a user, a container trashed after I picked it does not take the subsamples", async ({
    flowOperationsEnabled: _,
    pageInventory,
    clientInventory,
    componentToasts,
  }) => {
    const sample = await origin(clientInventory);
    const box = await gridContainer(clientInventory, 2, 2);
    const wizard = await aliquotToLocation(pageInventory, sample, 1);
    await wizard.location.pick(box.name);
    await wizard.location.grid.click("A1");
    await wizard.next();

    await clientInventory.deleteContainer(box.id);
    await wizard.perform();

    await expect(
      componentToasts.byVariant(
        "warning",
        `The new subsamples were created on your workbench, but could not be placed in ${box.name}.`,
      ),
    ).toBeVisible();
    expect(await placedLabels(clientInventory, box.id)).toEqual([]);
  });

  test.describe("pooling", () => {
    async function pool(pageInventory: InventoryPage, sample: { name: string; subSamples: Array<{ name: string }> }) {
      const wizard = await openWizard(
        pageInventory,
        sample.name,
        sample.subSamples.map((s) => s.name),
      );
      await wizard.chooseOperation("Pool");
      await wizard.goToLocation();
      return wizard;
    }

    test("As a user, the container both origins share is offered first", async ({
      flowOperationsEnabled: _,
      pageInventory,
      clientInventory,
    }) => {
      const sample = await origin(clientInventory, 2);
      const rack = await listContainer(clientInventory, "e2e-rack");
      for (const sub of sample.subSamples) {
        await clientInventory.moveSubSample(sub.id, { parentContainers: [{ id: rack.id }] });
      }

      const wizard = await pool(pageInventory, sample);

      await expect(wizard.location.placingIn).toContainText(rack.name);
      await expect(wizard.location.status).toHaveText(`The new subsamples will be placed in ${rack.name}.`);
    });

    // Which origin counts as first follows the wizard's origin order, not the order they were ticked in.
    test("As a user, origins in different containers offer one origin's container", async ({
      flowOperationsEnabled: _,
      pageInventory,
      clientInventory,
    }) => {
      const sample = await origin(clientInventory, 2);
      const [first, second] = sample.subSamples;
      const firstRack = await listContainer(clientInventory, "e2e-rack");
      const secondRack = await listContainer(clientInventory, "e2e-rack");
      await clientInventory.moveSubSample(first.id, { parentContainers: [{ id: firstRack.id }] });
      await clientInventory.moveSubSample(second.id, { parentContainers: [{ id: secondRack.id }] });

      const wizard = await pool(pageInventory, sample);

      await expect(wizard.location.placingIn).toHaveText(
        new RegExp(`^Placing in (${firstRack.name}|${secondRack.name}) `),
      );
    });
  });

  test.describe("remembered runs", () => {
    type Origin = Awaited<ReturnType<typeof origin>>;

    async function derive(pageInventory: InventoryPage, sample: Origin, index: number) {
      const wizard = await openWizard(pageInventory, sample.name, [sample.subSamples[index].name]);
      await wizard.chooseOperation("Derive");
      return wizard;
    }

    /** A first Derive run that remembers its values, placed in `container` or left on the workbench. */
    async function rememberDerive(
      pageInventory: InventoryPage,
      sample: Origin,
      container?: { name: string; locations?: string[] },
    ) {
      const wizard = await derive(pageInventory, sample, 0);
      await wizard.goToLocation({ processName: uniqueName("e2e-process") });
      if (container) {
        await wizard.location.pick(container.name);
        if (container.locations) await wizard.location.grid.click(...container.locations);
      }
      await wizard.next();
      await wizard.rememberCheckbox.check();
      await wizard.perform();
    }

    test("As a user, a remembered list container gives a one-click run", async ({
      flowOperationsEnabled: _,
      pageInventory,
      clientInventory,
    }) => {
      const sample = await origin(clientInventory, 2);
      const shelf = await listContainer(clientInventory);
      await rememberDerive(pageInventory, sample, { name: shelf.name });

      const wizard = await derive(pageInventory, sample, 1);

      await wizard.expectFastPath();
      expect(await wizard.confirmedLocation()).toBe(shelf.name);
      await wizard.perform();
      await expect
        .poll(async () => (await clientInventory.searchByParent(shelf.globalId, "SUBSAMPLE")).records.length)
        .toBe(2);
    });

    test("As a user, a remembered grid container opens on the Location step", async ({
      flowOperationsEnabled: _,
      pageInventory,
      clientInventory,
    }) => {
      const sample = await origin(clientInventory, 2);
      const box = await gridContainer(clientInventory, 2, 2);
      await rememberDerive(pageInventory, sample, { name: box.name, locations: ["A1"] });

      const wizard = await derive(pageInventory, sample, 1);

      await wizard.expectStep("Location");
      await expect(wizard.location.placingIn).toContainText(box.name);
      await expect(wizard.location.status).toHaveText(`Select 1 more location in ${box.name}.`);
      await expect(wizard.nextButton).toBeDisabled();
      await wizard.reviewEditButton.click();
      await wizard.expectStep("Details");
    });

    test("As a user, I am told when the remembered container can no longer be used", async ({
      flowOperationsEnabled: _,
      pageInventory,
      clientInventory,
    }) => {
      const sample = await origin(clientInventory, 2);
      const box = await gridContainer(clientInventory, 1, 1);
      await rememberDerive(pageInventory, sample, { name: box.name, locations: ["A1"] });

      const wizard = await derive(pageInventory, sample, 1);

      await wizard.expectStep("Location");
      await expect(wizard.rememberedContainerNote).toHaveText(
        `The remembered container "${box.name}" can no longer take the new subsamples. Choose another location, or leave them on your workbench.`,
      );
      await expect(wizard.location.searchInput).toBeVisible();
      await expect(wizard.location.placingIn).toBeHidden();
      await expect(wizard.nextButton).toBeDisabled();
      await wizard.location.chooseWorkbench();
      await expect(wizard.nextButton).toBeEnabled();
    });

    test("As a user, a remembered workbench wins over the origin's own container", async ({
      flowOperationsEnabled: _,
      pageInventory,
      clientInventory,
    }) => {
      const sample = await origin(clientInventory, 2);
      await rememberDerive(pageInventory, sample);
      const rack = await listContainer(clientInventory, "e2e-rack");
      await clientInventory.moveSubSample(sample.subSamples[1].id, { parentContainers: [{ id: rack.id }] });

      const wizard = await derive(pageInventory, sample, 1);

      await wizard.expectFastPath();
      expect(await wizard.confirmedLocation()).toBe("Your workbench");
    });
  });

  test("As a user, I cannot run the wizard on subsamples it is already open on elsewhere", async ({
    flowOperationsEnabled: _,
    pageInventory,
    clientInventory,
    browser,
    browserContextOptions,
    storageState,
  }) => {
    const sample = await origin(clientInventory);
    const first = await aliquotToLocation(pageInventory, sample);

    const otherTab = await browser.newContext({ ...browserContextOptions, storageState });
    try {
      // A second tab of the same signed-in user, which fixtures cannot provide.
      const otherPage = await otherTab.newPage();
      const otherInventory = new InventoryPage(otherPage);
      await otherInventory.openSearch("SUBSAMPLE");
      await otherInventory.searchPanel.search(sample.name);
      await otherInventory.searchPanel.selectItem(sample.subSamples[0].name);
      await otherInventory.searchPanel.batchActions.clickMoreAction("Process");
      await expect(
        new ToastsComponent(otherPage).byText(
          "These subsamples are open for editing elsewhere, perhaps in another tab. Close that editor and try again.",
        ),
      ).toBeVisible();
      await expect(otherPage.getByRole("dialog")).toHaveCount(0);
      // The refused action leaves the More actions menu open.
      await otherPage.keyboard.press("Escape");

      await first.cancel();
      const retried = await otherInventory.searchPanel.batchActions.openProcessWizard();
      await retried.chooseOperation("Aliquot");
      await retried.expectStep("Details");
    } finally {
      await otherTab.close();
    }
  });

  test("As a user, Process is not offered while operations are not enabled", async ({
    flowOperationsDisabled: _,
    pageInventory,
    clientInventory,
  }) => {
    const sample = await origin(clientInventory);
    await pageInventory.openSearch("SUBSAMPLE");
    await pageInventory.searchPanel.search(sample.name);
    await pageInventory.searchPanel.selectItem(sample.subSamples[0].name);

    await expect(await pageInventory.searchPanel.batchActions.moreActionItem("Duplicate")).toBeVisible();
    await expect(await pageInventory.searchPanel.batchActions.moreActionItem("Process")).toHaveCount(0);
  });

  test("As a user, another user's unshared container is not offered", async ({
    flowOperationsEnabled: _,
    pageInventory,
    clientInventory,
    clientSysadmin,
    apiContext,
  }) => {
    const stranger = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2ePlaceStranger");
    const strangerBox = await gridContainer(new InventoryClient(apiContext, stranger.apiKey), 2, 2);
    const sample = await origin(clientInventory);
    const wizard = await aliquotToLocation(pageInventory, sample);

    await wizard.location.chooseContainer();
    await wizard.location.search(strangerBox.name);

    await expect(wizard.root.getByRole("treeitem", { name: strangerBox.name })).toHaveCount(0);
  });

  test.describe("with a groupmate", () => {
    /**
     * A PI who runs a lab group the test user is an ordinary member of. Group membership is read at
     * sign-in, so the test user's own pages are opened afterwards with a fresh session.
     */
    async function groupmate(clientSysadmin: SysadminClient, apiContext: APIRequestContext, appUser: AppUser) {
      const pi = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2ePlacePi");
      await clientSysadmin.createGroup({
        displayName: uniqueName("e2ePlaceGroup"),
        type: "LAB_GROUP",
        users: [
          { username: pi.username, roleInGroup: "PI" },
          { username: appUser.username, roleInGroup: "DEFAULT" },
        ],
      });
      const user: AppUser = {
        username: pi.username,
        password: pi.password,
        apiKey: pi.apiKey,
        roles: ["ROLE_PI", "ROLE_USER"],
      };
      return { user, client: new InventoryClient(apiContext, pi.apiKey) };
    }

    test("As a user, a groupmate's container kept to its owner is not offered", async ({
      flowOperationsEnabled: _,
      clientInventory,
      clientSysadmin,
      apiContext,
      appUser,
      pageInventoryForUser,
    }) => {
      const pi = await groupmate(clientSysadmin, apiContext, appUser);
      const ownerOnly = await gridContainer(pi.client, 2, 2);
      await pi.client.updateContainer(ownerOnly.id, { sharingMode: "OWNER_ONLY", sharedWith: [] });
      const sample = await origin(clientInventory);
      const inventory = await pageInventoryForUser(appUser);
      const wizard = await aliquotToLocation(inventory, sample);

      await wizard.location.chooseContainer();
      await wizard.location.search(ownerOnly.name);

      await expect(wizard.root.getByRole("treeitem", { name: ownerOnly.name })).toHaveCount(0);
    });

    test("As a user racing a groupmate for the last location, only one of us gets it", async ({
      flowOperationsEnabled: _,
      clientInventory,
      clientSysadmin,
      apiContext,
      appUser,
      pageInventoryForUser,
    }) => {
      const pi = await groupmate(clientSysadmin, apiContext, appUser);
      const tube = await gridContainer(pi.client, 1, 1);
      const mine = await origin(clientInventory);
      const theirs = await pi.client.createSample({
        name: uniqueName("e2e-place-theirs"),
        newSampleSubSamplesCount: 1,
      });

      const openMove = async (user: AppUser, sampleName: string, subSampleName: string) => {
        const inventory = await pageInventoryForUser(user);
        await inventory.openSearch("SUBSAMPLE");
        await inventory.searchPanel.search(sampleName);
        await inventory.searchPanel.selectItem(subSampleName);
        const move = await inventory.searchPanel.batchActions.openMoveDialog();
        await move.selectGridDestination(tube.name);
        await move.grid.click("A1");
        return move;
      };
      const myMove = await openMove(appUser, mine.name, mine.subSamples[0].name);
      const theirMove = await openMove(pi.user, theirs.name, theirs.subSamples[0].name);

      await myMove.confirmMove();
      await theirMove.moveButton.click();

      await expect(theirMove.root).toBeVisible();
      const occupants = (await pi.client.getContainer(tube.id)).locations?.flatMap((l) =>
        l.content ? [l.content.globalId] : [],
      );
      expect(occupants).toEqual([mine.subSamples[0].globalId]);
    });
  });

  test.describe("by keyboard", () => {
    async function pickedGrid(
      pageInventory: InventoryPage,
      clientInventory: InventoryClient,
      { count, occupied = [] }: { count: number; occupied?: string[] },
    ) {
      const sample = await origin(clientInventory);
      const box = await gridContainer(clientInventory, 3, 3);
      for (const at of occupied) {
        const filler = await origin(clientInventory);
        const match = /^([A-Z])(\d+)$/.exec(at);
        if (!match) throw new Error(`"${at}" is not a grid label.`);
        await clientInventory.moveSubSample(filler.subSamples[0].id, {
          parentContainers: [{ id: box.id }],
          parentLocation: { coordX: Number(match[2]), coordY: match[1].charCodeAt(0) - 64 },
        });
      }
      const wizard = await aliquotToLocation(pageInventory, sample, count);
      await wizard.location.pick(box.name);
      await wizard.location.grid.tabIn(wizard.location.changeContainerButton);
      return { wizard, box, grid: wizard.location.grid };
    }

    test("As a keyboard user, I can pick non-adjacent locations and perform the aliquot", async ({
      flowOperationsEnabled: _,
      pageInventory,
      clientInventory,
    }) => {
      const { wizard, box, grid } = await pickedGrid(pageInventory, clientInventory, { count: 2 });

      await grid.expectSelected([]);
      await grid.press("Space", "ArrowRight", "ArrowRight", "ArrowDown", "ArrowDown", "Space");

      await expect(wizard.location.status).toHaveText(`The new subsamples will be placed in ${box.name}.`);
      await wizard.next();
      expect(await wizard.confirmedLocation()).toBe(`${box.name}: A1, C3`);
      await wizard.perform();
      await expect.poll(() => placedLabels(clientInventory, box.id)).toEqual(["A1", "C3"]);
    });

    test("As a keyboard user, I hear why a location was not picked", async ({
      flowOperationsEnabled: _,
      pageInventory,
      clientInventory,
    }) => {
      const { wizard, box, grid } = await pickedGrid(pageInventory, clientInventory, { count: 2, occupied: ["A1"] });

      await grid.press("Space");
      await expect(grid.announcement()).toHaveText("This location is not empty.");
      await grid.press("ArrowRight", "Space");
      await expect(wizard.location.status).toHaveText(`Select 1 more location in ${box.name}.`);
      await grid.press("ArrowRight", "Space", "ArrowDown", "Space");

      await expect(grid.announcement()).toHaveText("Selection limit reached: 2 locations already selected.");
      await grid.expectSelected(["A2", "A3"]);
      await expect(wizard.location.status).toHaveText(`The new subsamples will be placed in ${box.name}.`);
    });

    test("As a keyboard user, Escape clears my picks and keeps the wizard open", async ({
      flowOperationsEnabled: _,
      pageInventory,
      clientInventory,
    }) => {
      const { wizard, box, grid } = await pickedGrid(pageInventory, clientInventory, { count: 2 });
      await grid.press("Space", "ArrowRight", "ArrowRight", "ArrowDown", "ArrowDown", "Space");

      await grid.press("Escape");

      await grid.expectSelected([]);
      await expect(wizard.location.status).toHaveText(`Select 2 more locations in ${box.name}.`);
      await wizard.expectStep("Location");
      await expect(wizard.nextButton).toBeDisabled();
    });

    test("As a user, clicking a location gives it keyboard focus", async ({
      flowOperationsEnabled: _,
      pageInventory,
      clientInventory,
    }) => {
      const { grid } = await pickedGrid(pageInventory, clientInventory, { count: 2 });

      await grid.click("B2");
      await grid.press("ArrowRight", "Space");

      await grid.expectSelected(["B2", "B3"]);
    });

    test.describe("on a short screen", () => {
      test.use({ viewport: { width: 1192, height: 738 } });

      test("As a keyboard user, the grid's hint never covers the wizard's buttons", async ({
        flowOperationsEnabled: _,
        pageInventory,
        clientInventory,
      }) => {
        const { wizard, grid } = await pickedGrid(pageInventory, clientInventory, { count: 1 });

        await expect(grid.tips()).toBeVisible();
        const hint = await grid.tips().boundingBox();
        const next = await wizard.nextButton.boundingBox();
        if (!hint || !next) throw new Error("The hint and the Next button must both be rendered.");
        expect(hint.y + hint.height).toBeLessThanOrEqual(next.y);

        await grid.press("ArrowRight");
        await expect(grid.tips()).toBeHidden();
      });
    });

    test("As a keyboard user, the tips describe selecting, not drag-and-drop", async ({
      flowOperationsEnabled: _,
      pageInventory,
      clientInventory,
    }) => {
      const { grid } = await pickedGrid(pageInventory, clientInventory, { count: 1 });

      await grid.press("Space");

      await expect(grid.root).toHaveAccessibleDescription(
        "Use the arrow keys to move. Press Space to select or deselect a location. Hold Shift to select a range. Press Escape to clear selection.",
      );
    });
  });

  test("As a mouse user, I can click Next straight after picking a location", async ({
    flowOperationsEnabled: _,
    pageInventory,
    clientInventory,
  }) => {
    const sample = await origin(clientInventory);
    const box = await gridContainer(clientInventory, 2, 2);
    const wizard = await aliquotToLocation(pageInventory, sample, 1);
    await wizard.location.pick(box.name);

    await wizard.location.grid.click("A1");

    await expect(wizard.location.grid.tips()).toBeHidden();
    await wizard.nextButton.click({ timeout: 5_000 });
    await wizard.expectStep("Confirm");
  });

  test("As a user, the new subsamples stay on my workbench by default", async ({
    flowOperationsEnabled: _,
    pageInventory,
    clientInventory,
  }) => {
    const sample = await origin(clientInventory);
    const wizard = await aliquotToLocation(pageInventory, sample);

    await expect(wizard.location.workbenchRadio).toBeChecked();
    await wizard.next();
    expect(await wizard.confirmedLocation()).toBe("Your workbench");
    await wizard.perform();

    const bench = (await clientInventory.getSubSample(sample.subSamples[0].id)).parentContainers?.[0];
    const created = await clientInventory.searchByName(`${sample.name} aliquot`, "SUBSAMPLE");
    expect(created.records.length).toBeGreaterThan(0);
    for (const record of created.records) {
      expect((await clientInventory.getSubSample(record.id)).parentContainers?.[0].id).toBe(bench?.id);
    }
  });

  test("As a user, I can change the container I picked", async ({
    flowOperationsEnabled: _,
    pageInventory,
    clientInventory,
  }) => {
    const sample = await origin(clientInventory);
    const box = await gridContainer(clientInventory, 2, 2);
    const shelf = await listContainer(clientInventory);
    const wizard = await aliquotToLocation(pageInventory, sample, 2);
    await wizard.location.pick(box.name);
    await expect(wizard.location.grid.root).toBeVisible();

    await wizard.location.changeContainer();

    await expect(wizard.location.placingIn).toBeHidden();
    await expect(wizard.location.status).toBeHidden();
    await expect(wizard.location.grid.root).toBeHidden();
    await expect(wizard.nextButton).toBeDisabled();
    await wizard.location.pick(shelf.name);
    await expect(wizard.location.status).toHaveText(`The new subsamples will be placed in ${shelf.name}.`);
    await expect(wizard.nextButton).toBeEnabled();
  });

  test("As a user, the container my subsample is in is offered first", async ({
    flowOperationsEnabled: _,
    pageInventory,
    clientInventory,
  }) => {
    const sample = await origin(clientInventory);
    const rack = await listContainer(clientInventory, "e2e-rack");
    await clientInventory.moveSubSample(sample.subSamples[0].id, { parentContainers: [{ id: rack.id }] });

    const wizard = await aliquotToLocation(pageInventory, sample);

    await expect(wizard.location.containerRadio).toBeChecked();
    await expect(wizard.location.placingIn).toContainText(rack.name);
    await expect(wizard.location.status).toHaveText(`The new subsamples will be placed in ${rack.name}.`);
    await expect(wizard.nextButton).toBeEnabled();
  });
});
