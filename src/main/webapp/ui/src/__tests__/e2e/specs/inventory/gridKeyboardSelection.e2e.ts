import { expect, type Page } from "@playwright/test";
import type { InventoryClient } from "@/__tests__/e2e/api/clients/InventoryClient";
import type { ApiInventoryGridLocation, ApiInventorySubSample } from "@/__tests__/e2e/api/models/inventory";
import type { MoveDialogComponent } from "@/__tests__/e2e/components/inventory/MoveDialogComponent";
import { test } from "@/__tests__/e2e/fixtures/flows";
import type { InventoryPage } from "@/__tests__/e2e/pageObjects/inventory/InventoryPage";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

/** "B3" as the API addresses it: coordX is the column, coordY the row, both 1-based. */
function coord(label: string): ApiInventoryGridLocation {
  const match = /^([A-Z])(\d+)$/.exec(label);
  if (!match) throw new Error(`"${label}" is not a grid label.`);
  return { coordX: Number(match[2]), coordY: match[1].charCodeAt(0) - 64 };
}

function label({ coordX, coordY }: ApiInventoryGridLocation): string {
  return `${String.fromCharCode(64 + coordY)}${coordX}`;
}

async function gridContainer(client: InventoryClient, size: number, occupied: string[] = []) {
  const container = await client.createContainer({
    name: uniqueName("e2e-grid-keys"),
    cType: "GRID",
    gridLayout: { columnsNumber: size, rowsNumber: size },
  });
  for (const at of occupied) {
    const filler = await client.createSample({ name: uniqueName("e2e-grid-filler"), newSampleSubSamplesCount: 1 });
    await client.moveSubSample(filler.subSamples[0].id, {
      parentContainers: [{ id: container.id }],
      parentLocation: coord(at),
    });
  }
  return container;
}

async function subSamples(client: InventoryClient, count: number) {
  const sample = await client.createSample({ name: uniqueName("e2e-grid-move"), newSampleSubSamplesCount: count });
  return { name: sample.name, subSamples: sample.subSamples };
}

async function openMoveInto(
  pageInventory: InventoryPage,
  sampleName: string,
  count: number,
  containerName: string,
): Promise<{ move: MoveDialogComponent }> {
  await pageInventory.openSearch("SUBSAMPLE");
  await pageInventory.searchPanel.search(sampleName);
  for (let i = 1; i <= count; i++) {
    await pageInventory.searchPanel.selectItem(`${sampleName}.0${i}`);
  }
  const move = await pageInventory.searchPanel.batchActions.openMoveDialog();
  await move.selectGridDestination(containerName);
  return { move };
}

async function locationsOf(client: InventoryClient, subs: ApiInventorySubSample[]): Promise<string[]> {
  const located = await Promise.all(subs.map((s) => client.getSubSample(s.id)));
  return located.map((s) => (s.parentLocation ? label(s.parentLocation) : "none")).sort();
}

function countBulkRequests(page: Page): () => number {
  let count = 0;
  page.on("request", (req) => {
    if (req.method() === "POST" && new URL(req.url()).pathname.endsWith("/api/inventory/v1/bulk")) count += 1;
  });
  return () => count;
}

/** The progress alert's Expand button is the last control before the grid. */
async function tabIntoMoveGrid(move: MoveDialogComponent) {
  await move.grid.tabIn(move.root.getByRole("button", { name: "Expand", exact: true }));
}

test.describe("Grid keyboard selection", { tag: tags.INVENTORY }, () => {
  test.describe("in the Move dialog", () => {
    test.describe("on a phone too", { tag: tags.MOBILE }, () => {
      test("As a user, I can move two subsamples into non-adjacent grid locations by mouse", async ({
        pageInventory,
        clientInventory,
        componentToasts,
      }) => {
        const box = await gridContainer(clientInventory, 3);
        const { name, subSamples: subs } = await subSamples(clientInventory, 2);
        const { move } = await openMoveInto(pageInventory, name, 2, box.name);

        await move.grid.click("A1", "C3");

        await expect(move.progressAlert).toContainText("Selection complete.");
        await move.confirmMove();
        await expect(componentToasts.byText("Subsamples successfully moved.")).toBeVisible();
        expect(await locationsOf(clientInventory, subs)).toEqual(["A1", "C3"]);
      });
    });

    test("As a keyboard user, I can pick non-adjacent locations with Space and move into them", async ({
      pageInventory,
      clientInventory,
      componentToasts,
    }) => {
      const box = await gridContainer(clientInventory, 3);
      const { name, subSamples: subs } = await subSamples(clientInventory, 2);
      const { move } = await openMoveInto(pageInventory, name, 2, box.name);

      await tabIntoMoveGrid(move);
      await expect.poll(() => move.grid.focusedLabel()).toBe("A1");
      await move.grid.expectSelected([]);
      await move.grid.press("Space", "ArrowRight", "ArrowRight", "ArrowDown", "ArrowDown", "Space");

      await move.grid.expectSelected(["A1", "C3"]);
      await expect(move.progressAlert).toContainText("Selection complete.");
      await move.confirmMove();
      await expect(componentToasts.byText("Subsamples successfully moved.")).toBeVisible();
      expect(await locationsOf(clientInventory, subs)).toEqual(["A1", "C3"]);
    });

    test("As a keyboard user, arrows only move focus and a refused Space is announced", async ({
      pageInventory,
      clientInventory,
    }) => {
      const box = await gridContainer(clientInventory, 3);
      const { name } = await subSamples(clientInventory, 2);
      const { move } = await openMoveInto(pageInventory, name, 2, box.name);

      await tabIntoMoveGrid(move);
      await move.grid.press("ArrowRight", "ArrowDown");
      await expect.poll(() => move.grid.focusedLabel()).toBe("B2");
      await move.grid.expectSelected([]);

      await move.grid.press("Space", "ArrowRight", "Space", "ArrowDown", "Space");

      await move.grid.expectSelected(["B2", "B3"]);
      await expect(move.grid.announcement()).toHaveText("Selection limit reached: 2 locations already selected.");
    });

    test("As a keyboard user moving one subsample, a second Space moves my pick", async ({
      pageInventory,
      clientInventory,
    }) => {
      const box = await gridContainer(clientInventory, 3);
      const { name } = await subSamples(clientInventory, 1);
      const { move } = await openMoveInto(pageInventory, name, 1, box.name);

      await tabIntoMoveGrid(move);
      await move.grid.press("Space", "ArrowRight", "Space");

      await move.grid.expectSelected(["A2"]);
      await expect(move.grid.announcement()).toHaveCount(0);
    });

    test("As a keyboard user, Space on an occupied location is refused and announced", async ({
      pageInventory,
      clientInventory,
    }) => {
      const box = await gridContainer(clientInventory, 3, ["A1"]);
      const { name } = await subSamples(clientInventory, 1);
      const { move } = await openMoveInto(pageInventory, name, 1, box.name);

      await tabIntoMoveGrid(move);
      await expect.poll(() => move.grid.focusedLabel()).toBe("A1");
      await move.grid.press("Space");

      await expect(move.grid.announcement()).toHaveText("This location is not empty.");
      await move.grid.expectSelected([]);
      await expect(move.grid.tips()).toContainText("Press Space to select or deselect a location.");
    });

    test("As a keyboard user, Escape clears my picks without closing the Move dialog", async ({
      pageInventory,
      clientInventory,
    }) => {
      const box = await gridContainer(clientInventory, 3);
      const { name } = await subSamples(clientInventory, 2);
      const { move } = await openMoveInto(pageInventory, name, 2, box.name);

      await tabIntoMoveGrid(move);
      await move.grid.press("Space", "ArrowRight", "ArrowRight", "ArrowDown", "ArrowDown", "Space");
      await move.grid.expectSelected(["A1", "C3"]);

      await move.grid.press("Escape");
      await move.grid.expectSelected([]);
      await expect(move.progressAlert).toContainText("(0/2 placed)");
      await expect(move.moveButton).toBeDisabled();

      await move.grid.press("Escape");
      await expect(move.root).toBeVisible();
      await move.cancel();
    });

    test("As a keyboard user, deselecting a preview keeps focus in the grid", async ({
      pageInventory,
      clientInventory,
    }) => {
      const box = await gridContainer(clientInventory, 3);
      const { name } = await subSamples(clientInventory, 1);
      const { move } = await openMoveInto(pageInventory, name, 1, box.name);

      await tabIntoMoveGrid(move);
      await move.grid.press("Space");
      await move.grid.expectSelected(["A1"]);
      await move.grid.press("Space");
      await move.grid.expectSelected([]);
      await expect.poll(() => move.grid.focusedLabel()).toBe("A1");

      await move.grid.press("ArrowRight", "Space");

      await move.grid.expectSelected(["A2"]);
      await expect.poll(() => move.grid.focusedLabel()).toBe("A2");
    });

    test("As a keyboard user, Shift+Arrow adds a range to earlier picks up to the limit", async ({
      pageInventory,
      clientInventory,
    }) => {
      const box = await gridContainer(clientInventory, 3);
      const { name } = await subSamples(clientInventory, 3);
      const { move } = await openMoveInto(pageInventory, name, 3, box.name);

      await tabIntoMoveGrid(move);
      await move.grid.press("Space", "ArrowRight", "ArrowDown", "Shift+ArrowRight");
      await move.grid.expectSelected(["A1", "B2", "B3"]);

      await move.grid.press("Shift+ArrowDown");
      await expect(move.grid.announcement()).toHaveText("Selection limit reached: 3 locations already selected.");
      await move.grid.expectSelected(["A1", "B2", "B3"]);

      await move.grid.press("Shift+ArrowUp", "Shift+ArrowLeft");
      await move.grid.expectSelected(["A1", "B2"]);
    });

    test("As a user, dragging an item inside the Move dialog's grid does not move it", async ({
      pageInventory,
      clientInventory,
      page,
    }) => {
      const box = await gridContainer(clientInventory, 3, ["A1"]);
      const { name } = await subSamples(clientInventory, 1);
      const bulkRequests = countBulkRequests(page);
      const { move } = await openMoveInto(pageInventory, name, 1, box.name);

      await move.grid.cell("A1").dragTo(move.grid.cell("B2"));
      await move.cancel();

      expect(bulkRequests()).toBe(0);
      const contents = await clientInventory.getContainer(box.id);
      expect(contents.locations?.filter((l) => l.content).map(label)).toEqual(["A1"]);
    });

    test("As a user, the container page keeps its own keyboard model after a Move dialog", async ({
      pageInventory,
      clientInventory,
    }) => {
      const box = await gridContainer(clientInventory, 3);
      const { name } = await subSamples(clientInventory, 1);
      const { move } = await openMoveInto(pageInventory, name, 1, box.name);
      await move.cancel();

      await pageInventory.openRecord("CONTAINER", box.name);
      const grid = pageInventory.detailsPanel.contentGrid();
      await grid.waitForLoaded();
      await grid.tabIn(
        pageInventory.detailsPanel
          .section("Locations and Content")
          .getByRole("button", { name: "More selection options", exact: true }),
      );

      await grid.expectSelected(["A1"]);
      await grid.press("ArrowRight");
      await grid.expectSelected(["A2"]);
      await expect(grid.tips()).toContainText("Press Space to enter drag-and-drop mode.");
    });
  });

  test.describe("on the container page", () => {
    async function openGrid(pageInventory: InventoryPage, containerName: string) {
      await pageInventory.openRecord("CONTAINER", containerName);
      const grid = pageInventory.detailsPanel.contentGrid();
      await grid.waitForLoaded();
      await grid.tabIn(
        pageInventory.detailsPanel
          .section("Locations and Content")
          .getByRole("button", { name: "More selection options", exact: true }),
      );
      return grid;
    }

    test("As a keyboard user, I can drag an item to another location and keep using the arrows", async ({
      pageInventory,
      clientInventory,
      page,
    }) => {
      const box = await gridContainer(clientInventory, 3, ["A1"]);
      const bulkRequests = countBulkRequests(page);
      const grid = await openGrid(pageInventory, box.name);
      await grid.expectSelected(["A1"]);

      await grid.press("Space");
      await expect(grid.tips()).toContainText("Press Enter to drop items.");
      await grid.press("ArrowRight", "Enter");

      await expect.poll(bulkRequests).toBe(1);
      await expect
        .poll(async () => (await clientInventory.getContainer(box.id)).locations?.filter((l) => l.content).map(label))
        .toEqual(["A2"]);
      await grid.press("ArrowDown");
      await expect.poll(() => grid.selectedLabels()).toHaveLength(1);
    });

    test("As a keyboard user, Escape leaves only the focused location selected", async ({
      pageInventory,
      clientInventory,
    }) => {
      const box = await gridContainer(clientInventory, 3);
      const grid = await openGrid(pageInventory, box.name);

      await grid.press("Shift+ArrowDown");
      await grid.expectSelected(["A1", "B1"]);
      await grid.press("Escape");

      await grid.expectSelected(["B1"]);
    });

    test("As a keyboard user, arrows move the selection and Shift+Arrow selects a range", async ({
      pageInventory,
      clientInventory,
    }) => {
      const box = await gridContainer(clientInventory, 3);
      const grid = await openGrid(pageInventory, box.name);

      await grid.press("ArrowRight");
      await grid.expectSelected(["A2"]);
      await grid.press("Shift+ArrowDown");
      await grid.expectSelected(["A2", "B2"]);

      await expect(grid.root.getByRole("columnheader", { name: "Row", exact: true })).toHaveCount(1);
      await expect(grid.root.getByRole("gridcell")).toHaveCount(9);
    });
  });
});
