import "@/stores/stores/RootStore";
import "@/__tests__/__mocks__/resizeObserver";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { runInAction } from "mobx";
import { afterEach, describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { prepareContainer } from "@/Inventory/components/Operations/placement";
import SearchContext from "@/stores/contexts/Search";
import { makeMockSubSample } from "@/stores/models/__tests__/SubSampleModel/mocking";
import type ContainerModel from "@/stores/models/ContainerModel";
import type Search from "@/stores/models/Search";
import getRootStore from "@/stores/stores/getRootStore";
import materialTheme from "@/theme";
import ContentGrid from "../ContentGrid";
import { boxWithA1Taken, emptyBox } from "./gridFixtures";

function renderGrid(box: ContainerModel) {
  render(
    <ThemeProvider theme={materialTheme}>
      <SearchContext.Provider
        value={{
          search: box.contentSearch,
          scopedResult: box,
          differentSearchForSettingActiveResult: box.contentSearch,
        }}
      >
        <ContentGrid />
      </SearchContext.Provider>
    </ThemeProvider>,
  );
}

const draggableIn = (cell: HTMLElement) => within(cell).getByRole("button");

/** The location cells, row by row; the header row's corner cell is not one. */
const cells = () => screen.getAllByRole("cell").filter((c) => c.hasAttribute("aria-selected"));

describe("ContentGrid", () => {
  describe("drag-and-drop", () => {
    it("lets the user drag an item to another location in the container's own grid", () => {
      const box = boxWithA1Taken();
      renderGrid(box);
      expect(draggableIn(cells()[0])).toHaveAttribute("aria-disabled", "false");
    });

    it("is off in a grid used only to choose locations for new records", () => {
      const box = boxWithA1Taken();
      prepareContainer(box, 1);
      renderGrid(box);
      expect(draggableIn(cells()[0])).toHaveAttribute("aria-disabled", "true");
    });
  });

  describe("keyboard selection", () => {
    const selectedCount = () => cells().filter((c) => c.getAttribute("aria-selected") === "true").length;

    it("selects the focused location and Shift+Arrow selects a rectangle, filled or empty", async () => {
      const user = userEvent.setup();
      renderGrid(boxWithA1Taken());
      await user.tab();
      expect(cells()[0]).toHaveAttribute("aria-selected", "true");
      await user.keyboard("{Shift>}{ArrowDown}{/Shift}");
      expect(selectedCount()).toBe(2);
    });

    it("on the container page, moves the selection with the arrow keys and enters drag-and-drop with Space", async () => {
      const user = userEvent.setup();
      renderGrid(boxWithA1Taken());
      await user.tab();
      await user.keyboard("{ArrowRight}");
      expect(cells().map((c) => c.getAttribute("aria-selected"))).toEqual(["false", "true", "false", "false"]);
      expect(screen.getByText("inventory:container.content.keyboard.tips")).toBeVisible();
      await user.keyboard(" {ArrowDown}");
      expect(cells().map((c) => c.getAttribute("aria-selected"))).toEqual(["false", "true", "false", "false"]);
      expect(screen.getByText("inventory:container.content.keyboard.dragTips")).toBeVisible();
    });

    describe("when choosing empty locations for new records", () => {
      it("is accessible", async () => {
        const box = boxWithA1Taken();
        prepareContainer(box, 1);
        renderGrid(box);
        await expectAccessible(document.body);
      });

      it("does not select a focused empty location once the selection limit is reached", async () => {
        const user = userEvent.setup();
        const box = emptyBox();
        prepareContainer(box, 1);
        box.locations?.[3].toggleSelected(true);
        renderGrid(box);
        await user.tab();
        expect(cells()[0]).toHaveAttribute("aria-selected", "false");
        expect(selectedCount()).toBe(1);
      });
    });

    describe("in the Move dialog", () => {
      /** Configures the grid as MoveStore.setIsMoving does for its destination panel. */
      function startMoving(box: ContainerModel, items: ReadonlyArray<ReturnType<typeof makeMockSubSample>>) {
        const { moveStore } = getRootStore();
        runInAction(() => {
          moveStore.isMoving = true;
          moveStore.search = { activeResult: box } as unknown as Search;
          moveStore.selectedResults = [...items];
          Object.assign(box.contentSearch.uiConfig, {
            selectionMode: "MULTIPLE",
            selectionLimit: items.length,
            onlyAllowSelectingEmptyLocations: true,
            dragAndDropDisabled: true,
          });
        });
      }

      afterEach(() => {
        runInAction(() => {
          const { moveStore } = getRootStore();
          moveStore.isMoving = false;
          moveStore.search = null;
          moveStore.selectedResults = [];
        });
      });

      it("keeps the first location's staged item when Shift+Arrow extends the selection", async () => {
        const user = userEvent.setup();
        const box = emptyBox();
        startMoving(box, [
          makeMockSubSample({ id: 101, globalId: "SS101" }),
          makeMockSubSample({ id: 102, globalId: "SS102" }),
        ]);
        box.findLocation(1, 1)?.toggleSelected(true);
        renderGrid(box);
        await user.tab();
        await user.keyboard("{Shift>}{ArrowRight}{/Shift}");
        expect(cells()[0]).toHaveAttribute("aria-selected", "true");
        expect(cells()[1]).toHaveAttribute("aria-selected", "true");
        const staged = (box.selectedLocations ?? []).map((l) => l.content?.globalId).sort();
        expect(staged).toEqual(["SS101", "SS102"]);
      });

      it("places each item being moved where Space selects", async () => {
        const user = userEvent.setup();
        const box = emptyBox();
        startMoving(box, [
          makeMockSubSample({ id: 1, globalId: "SS1" }),
          makeMockSubSample({ id: 2, globalId: "SS2" }),
        ]);
        renderGrid(box);
        await user.tab();
        await user.keyboard(" {ArrowRight}{ArrowDown} ");
        expect(box.selectedLocations?.map((l) => [l.coordX, l.coordY, l.content?.globalId])).toEqual([
          [1, 1, "SS1"],
          [2, 2, "SS2"],
        ]);
      });
    });

    describe("in a grid used only to choose locations", () => {
      it("lets Space toggle locations that are not next to each other", async () => {
        const user = userEvent.setup();
        const box = emptyBox();
        prepareContainer(box, 2);
        renderGrid(box);
        await user.tab();
        await user.keyboard(" {ArrowRight}{ArrowDown} ");
        expect(cells().map((c) => c.getAttribute("aria-selected"))).toEqual(["true", "false", "false", "true"]);
        expect(screen.getByText("inventory:container.content.keyboard.selectionTips")).toBeVisible();
      });

      it("lets Space deselect a location", async () => {
        const user = userEvent.setup();
        const box = emptyBox();
        prepareContainer(box, 2);
        renderGrid(box);
        await user.tab();
        await user.keyboard(" ");
        expect(selectedCount()).toBe(1);
        await user.keyboard(" ");
        expect(selectedCount()).toBe(0);
      });

      it("keeps locations picked earlier when Shift+Arrow selects a range", async () => {
        const user = userEvent.setup();
        const box = emptyBox();
        prepareContainer(box, 4);
        renderGrid(box);
        await user.tab();
        await user.keyboard(" {ArrowDown}{ArrowRight}{Shift>}{ArrowUp}{/Shift}");
        expect(cells().map((c) => c.getAttribute("aria-selected"))).toEqual(["true", "true", "false", "true"]);
      });

      it("deselects only the range's own locations when Shift+Arrow shrinks it", async () => {
        const user = userEvent.setup();
        const box = emptyBox();
        prepareContainer(box, 4);
        renderGrid(box);
        await user.tab();
        await user.keyboard("{ArrowDown} {ArrowUp}{Shift>}{ArrowRight}{ArrowLeft}{/Shift}");
        expect(cells().map((c) => c.getAttribute("aria-selected"))).toEqual(["true", "false", "true", "false"]);
      });

      it("counts locations picked earlier towards the limit when Shift+Arrow selects a range, and says so", async () => {
        const user = userEvent.setup();
        const box = emptyBox();
        prepareContainer(box, 2);
        renderGrid(box);
        await user.tab();
        await user.keyboard(" {ArrowDown}{Shift>}{ArrowRight}{/Shift}");
        expect(cells().map((c) => c.getAttribute("aria-selected"))).toEqual(["true", "false", "true", "false"]);
        expect(screen.getByText("inventory:container.content.keyboard.limitReached").parentElement).toHaveAttribute(
          "role",
          "status",
        );
      });

      it("does not let Shift+Arrow select more locations than the limit", async () => {
        const user = userEvent.setup();
        const box = boxWithA1Taken();
        prepareContainer(box, 1);
        renderGrid(box);
        await user.tab();
        await user.keyboard("{ArrowRight}{Shift>}{ArrowDown}{/Shift}");
        expect(cells()[1]).toHaveAttribute("aria-selected", "true");
        expect(selectedCount()).toBe(1);
      });

      it("does not let Space select more locations than the limit, and says why", async () => {
        const user = userEvent.setup();
        const box = emptyBox();
        prepareContainer(box, 1);
        renderGrid(box);
        await user.tab();
        await user.keyboard(" {ArrowRight}{ArrowDown} ");
        expect(cells().map((c) => c.getAttribute("aria-selected"))).toEqual(["true", "false", "false", "false"]);
        expect(screen.getByText("inventory:container.content.keyboard.limitReached").parentElement).toHaveAttribute(
          "role",
          "status",
        );
      });

      it("clears the explanation once the user moves on, and repeats it on the next refused Space", async () => {
        const user = userEvent.setup();
        const box = boxWithA1Taken();
        prepareContainer(box, 2);
        renderGrid(box);
        const explanation = () => screen.queryByText("inventory:container.content.keyboard.occupied");
        await user.tab();
        await user.keyboard(" ");
        const first = explanation();
        await user.keyboard(" ");
        expect(explanation()).not.toBeNull();
        expect(explanation()).not.toBe(first);
        await user.keyboard("{ArrowRight}");
        expect(explanation()).toBeNull();
        await user.keyboard("{ArrowLeft} {Escape}");
        expect(explanation()).toBeNull();
      });

      it("says why Space does not select an occupied location", async () => {
        const user = userEvent.setup();
        const box = boxWithA1Taken();
        prepareContainer(box, 2);
        renderGrid(box);
        await user.tab();
        await user.keyboard(" ");
        expect(screen.getByText("inventory:container.content.keyboard.occupied").parentElement).toHaveAttribute(
          "role",
          "status",
        );
      });

      it("does not select on focus or with the arrow keys, ignores occupied locations, and Escape clears everything", async () => {
        const user = userEvent.setup();
        const box = boxWithA1Taken();
        prepareContainer(box, 2);
        renderGrid(box);
        await user.tab();
        await user.keyboard(" {ArrowRight}");
        expect(selectedCount()).toBe(0);
        await user.keyboard(" {ArrowDown} ");
        expect(selectedCount()).toBe(2);
        await user.keyboard("{Escape}");
        expect(selectedCount()).toBe(0);
      });
    });
  });
});
