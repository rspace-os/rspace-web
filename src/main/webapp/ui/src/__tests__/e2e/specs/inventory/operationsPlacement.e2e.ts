import { expect } from "@playwright/test";
import type { InventoryClient } from "@/__tests__/e2e/api/clients/InventoryClient";
import type { ApiInventoryGridLocation } from "@/__tests__/e2e/api/models/inventory";
import type { ProcessWizardComponent } from "@/__tests__/e2e/components/inventory/ProcessWizardComponent";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import type { InventoryPage } from "@/__tests__/e2e/pageObjects/inventory/InventoryPage";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

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

    test("As a keyboard user, the tips describe selecting, not drag-and-drop", async ({
      flowOperationsEnabled: _,
      pageInventory,
      clientInventory,
    }) => {
      const { grid } = await pickedGrid(pageInventory, clientInventory, { count: 1 });

      await grid.press("Space");

      await expect(grid.tips()).toHaveText(
        "Use the arrow keys to move. Press Space to select or deselect a location. Hold Shift to select a range. Press Escape to clear selection.",
      );
    });

    test("As a mouse user, I can click Next while the grid has focus", {
      annotation: { type: "issue", description: "Keyboard tips cover the dialog buttons (e2e report F1)" },
    }, async ({ flowOperationsEnabled: _, pageInventory, clientInventory }) => {
      test.fail();
      const { wizard, grid } = await pickedGrid(pageInventory, clientInventory, { count: 1 });

      await grid.click("A1");

      await wizard.nextButton.click({ timeout: 5_000 });
    });
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
