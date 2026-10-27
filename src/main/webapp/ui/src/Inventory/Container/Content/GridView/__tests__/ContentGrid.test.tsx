import "@/stores/stores/RootStore";
import "@/__tests__/__mocks__/resizeObserver";
import Dialog from "@mui/material/Dialog";
import { ThemeProvider } from "@mui/material/styles";
import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { runInAction } from "mobx";
import { afterEach, describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { prepareContainer } from "@/Inventory/components/Operations/placement";
import SearchContext from "@/stores/contexts/Search";
import { makeMockSubSample } from "@/stores/models/__tests__/SubSampleModel/mocking";
import type ContainerModel from "@/stores/models/ContainerModel";
import materialTheme from "@/theme";
import ContentGrid from "../ContentGrid";
import { boxWithA1Taken, emptyBox, resetMove, stageMove } from "./gridFixtures";

function renderGrid(box: ContainerModel, { inDialogClosedBy }: { inDialogClosedBy?: () => void } = {}) {
  const grid = (
    <SearchContext.Provider
      value={{
        search: box.contentSearch,
        scopedResult: box,
        differentSearchForSettingActiveResult: box.contentSearch,
      }}
    >
      <ContentGrid />
    </SearchContext.Provider>
  );
  render(
    <ThemeProvider theme={materialTheme}>
      {inDialogClosedBy ? (
        <Dialog open onClose={inDialogClosedBy}>
          {grid}
        </Dialog>
      ) : (
        grid
      )}
    </ThemeProvider>,
  );
}

const draggableIn = (cell: HTMLElement) => within(cell).getByRole("button");

/** The location cells, row by row. */
const cells = () => screen.getAllByRole("cell");

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

  it("names the header row's corner cell for the row headers, so it is a column header and not a location", () => {
    renderGrid(boxWithA1Taken());
    expect(
      screen.getByRole("columnheader", { name: "inventory:container.content.rowHeaderLabel" }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole("columnheader")).toHaveLength(3);
    expect(cells()).toHaveLength(4);
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

    it("on the container page, a location emptied while it has focus is not selected", async () => {
      const user = userEvent.setup();
      const box = boxWithA1Taken();
      renderGrid(box);
      await user.tab();
      expect(cells()[0]).toHaveAttribute("aria-selected", "true");
      const a1 = box.findLocation(1, 1);
      if (!a1) throw new Error("A1 missing");
      // The item is dragged away with the keyboard: its location empties and is no longer selected.
      act(() => {
        runInAction(() => {
          a1.toggleSelected(false);
          a1.content = null;
        });
      });
      expect(cells()[0]).toHaveAttribute("aria-selected", "false");
      expect(selectedCount()).toBe(0);
    });

    it("on the container page, the arrow keys move the selection, and Space enters the grid's keyboard drag mode, after which the arrows stop moving it", async () => {
      const user = userEvent.setup();
      // jsdom lacks scrollIntoView, which dnd-kit calls when a keyboard drag starts.
      HTMLElement.prototype.scrollIntoView = () => {};
      renderGrid(boxWithA1Taken());
      await user.tab();
      await user.keyboard("{ArrowRight}");
      expect(cells().map((c) => c.getAttribute("aria-selected"))).toEqual(["false", "true", "false", "false"]);
      await user.keyboard("{ArrowLeft} {ArrowDown}");
      expect(cells().map((c) => c.getAttribute("aria-selected"))).toEqual(["true", "false", "false", "false"]);
      expect(screen.getByText("inventory:container.content.keyboard.dragTips")).toBeVisible();
      // Ends dnd-kit's keyboard drag, whose document listeners would otherwise outlive the test.
      await user.keyboard("{Escape}");
    });

    it("on the container page, Space on an empty location leaves the arrows working and says there is nothing to move", async () => {
      const user = userEvent.setup();
      renderGrid(boxWithA1Taken());
      await user.tab();
      await user.keyboard("{ArrowRight} ");
      expect(screen.getByText("inventory:container.content.keyboard.nothingToMove").parentElement).toHaveAttribute(
        "role",
        "status",
      );
      expect(screen.queryByText("inventory:container.content.keyboard.dragTips")).not.toBeInTheDocument();
      await user.keyboard("{ArrowDown}");
      expect(cells().map((c) => c.getAttribute("aria-selected"))).toEqual(["false", "false", "false", "true"]);
    });

    it("on the container page, Space does not start a drag while the selection includes an empty location", async () => {
      const user = userEvent.setup();
      const box = boxWithA1Taken();
      renderGrid(box);
      await user.tab();
      act(() => {
        box.findLocation(2, 1)?.toggleSelected(true);
      });
      await user.keyboard(" ");
      expect(screen.queryByText("inventory:container.content.keyboard.dragTips")).not.toBeInTheDocument();
      await user.keyboard("{ArrowDown}");
      expect(cells()[2]).toHaveAttribute("aria-selected", "true");
    });

    it("lets Escape reach the dialog around a container's own grid", async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();
      renderGrid(boxWithA1Taken(), { inDialogClosedBy: onClose });
      await user.tab();
      await user.keyboard("{Escape}");
      expect(onClose).toHaveBeenCalled();
    });

    it("leaves only the focused location selected after Escape on a container's own grid", async () => {
      const user = userEvent.setup();
      renderGrid(boxWithA1Taken());
      await user.tab();
      await user.keyboard("{Shift>}{ArrowDown}{/Shift}{Escape}");
      expect(selectedCount()).toBe(1);
      expect(cells()[2]).toHaveAttribute("aria-selected", "true");
    });

    describe("when choosing empty locations for new records", () => {
      it("is accessible", async () => {
        const box = boxWithA1Taken();
        prepareContainer(box, 1);
        renderGrid(box);
        await expectAccessible(document.body);
      });
    });

    describe("in the Move dialog", () => {
      afterEach(resetMove);

      it("keeps the first location's staged item when Shift+Arrow extends the selection", async () => {
        const user = userEvent.setup();
        const box = emptyBox();
        stageMove(box, [
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
        stageMove(box, [makeMockSubSample({ id: 1, globalId: "SS1" }), makeMockSubSample({ id: 2, globalId: "SS2" })]);
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
      });

      it("keeps focus on the first location when the arrows push past the grid's top-left edge", async () => {
        const user = userEvent.setup();
        const box = emptyBox();
        prepareContainer(box, 2);
        renderGrid(box);
        await user.tab();
        await user.keyboard("{ArrowLeft}{ArrowUp} ");
        expect(cells()[0]).toHaveAttribute("aria-selected", "true");
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

      it("starts the next Shift+Arrow range from the focused location after Escape", async () => {
        const user = userEvent.setup();
        const box = emptyBox();
        prepareContainer(box, 2);
        renderGrid(box);
        await user.tab();
        await user.keyboard("{Shift>}{ArrowRight}{/Shift}{Escape}{Shift>}{ArrowDown}{/Shift}");
        expect(cells().map((c) => c.getAttribute("aria-selected"))).toEqual(["false", "true", "false", "true"]);
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
        prepareContainer(box, 2);
        renderGrid(box);
        await user.tab();
        await user.keyboard(" {ArrowRight} {ArrowDown} ");
        expect(cells().map((c) => c.getAttribute("aria-selected"))).toEqual(["true", "true", "false", "false"]);
        expect(screen.getByText("inventory:container.content.keyboard.limitReached").parentElement).toHaveAttribute(
          "role",
          "status",
        );
      });

      it("moves the only pick to the focused location when the limit is one", async () => {
        const user = userEvent.setup();
        const box = emptyBox();
        prepareContainer(box, 1);
        renderGrid(box);
        await user.tab();
        await user.keyboard(" {ArrowRight} ");
        expect(cells().map((c) => c.getAttribute("aria-selected"))).toEqual(["false", "true", "false", "false"]);
        expect(screen.queryByText("inventory:container.content.keyboard.limitReached")).toBeNull();
      });

      it("repeats the explanation on consecutive refused Shift+Arrow presses", async () => {
        const user = userEvent.setup();
        const box = emptyBox();
        prepareContainer(box, 1);
        renderGrid(box);
        const explanation = () => screen.getByText("inventory:container.content.keyboard.limitReached");
        await user.tab();
        await user.keyboard(" {ArrowRight}{Shift>}{ArrowDown}");
        const first = explanation();
        await user.keyboard("{ArrowLeft}{/Shift}");
        expect(explanation()).not.toBe(first);
        expect(cells().map((c) => c.getAttribute("aria-selected"))).toEqual(["true", "false", "false", "false"]);
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

      it("clears the selection with Escape without closing the dialog it is in", async () => {
        const user = userEvent.setup();
        const box = emptyBox();
        prepareContainer(box, 2);
        const onClose = vi.fn();
        renderGrid(box, { inDialogClosedBy: onClose });
        await user.tab();
        await user.keyboard(" {Escape}");
        expect(selectedCount()).toBe(0);
        expect(onClose).not.toHaveBeenCalled();
      });

      describe("after a mouse click on a location", () => {
        it("acts on the arrow keys and Space from the clicked location", async () => {
          const user = userEvent.setup();
          const box = emptyBox();
          prepareContainer(box, 1);
          renderGrid(box);
          await user.click(cells()[3]);
          await user.keyboard("{ArrowLeft} ");
          expect(cells().map((c) => c.getAttribute("aria-selected"))).toEqual(["false", "false", "true", "false"]);
        });

        it("starts the next Shift+Arrow range from the clicked location", async () => {
          const user = userEvent.setup();
          const box = emptyBox();
          prepareContainer(box, 4);
          renderGrid(box);
          await user.tab();
          await user.keyboard("{Shift>}{ArrowRight}{/Shift}");
          await user.click(cells()[3]);
          await user.keyboard("{Shift>}{ArrowLeft}{/Shift}");
          expect(cells().map((c) => c.getAttribute("aria-selected"))).toEqual(["false", "false", "true", "true"]);
        });

        it("does not show the keyboard tips, which would cover the dialog's buttons", async () => {
          const user = userEvent.setup();
          const box = emptyBox();
          prepareContainer(box, 1);
          renderGrid(box);
          await user.click(cells()[3]);
          expect(screen.queryByRole("note")).not.toBeInTheDocument();
        });

        it("keeps Escape inside the grid", async () => {
          const user = userEvent.setup();
          const box = emptyBox();
          prepareContainer(box, 1);
          const onClose = vi.fn();
          renderGrid(box, { inDialogClosedBy: onClose });
          await user.click(cells()[2]);
          expect(screen.getByRole("grid")).toContainElement(document.activeElement as HTMLElement);
          await user.keyboard("{Escape}");
          expect(onClose).not.toHaveBeenCalled();
          expect(selectedCount()).toBe(0);
        });
      });
    });
  });
  describe("keyboard hint", () => {
    afterEach(() => {
      vi.useRealTimers();
    });

    it("shows when the grid gains keyboard focus and hides on the first arrow key", async () => {
      const user = userEvent.setup();
      renderGrid(boxWithA1Taken());
      await user.tab();
      expect(screen.getByRole("note")).toHaveTextContent("inventory:container.content.keyboard.tips");
      await user.keyboard("{ArrowRight}");
      expect(screen.queryByRole("note")).not.toBeInTheDocument();
    });

    it("hides after a few seconds", async () => {
      vi.useFakeTimers({ shouldAdvanceTime: true });
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      renderGrid(boxWithA1Taken());
      await user.tab();
      expect(screen.getByRole("note")).toBeInTheDocument();
      act(() => {
        vi.advanceTimersByTime(6000);
      });
      expect(screen.queryByRole("note")).not.toBeInTheDocument();
    });

    it("keeps the drop line visible through arrow presses in keyboard drag mode", async () => {
      const user = userEvent.setup();
      // jsdom lacks scrollIntoView, which dnd-kit calls when a keyboard drag starts.
      HTMLElement.prototype.scrollIntoView = () => {};
      renderGrid(boxWithA1Taken());
      await user.tab();
      await user.keyboard(" {ArrowRight}{ArrowDown}");
      expect(screen.getByRole("note")).toHaveTextContent("inventory:container.content.keyboard.dragTips");
      await user.keyboard("{Escape}");
    });

    it("leaves the full instructions in the grid's accessible description", () => {
      const box = emptyBox();
      prepareContainer(box, 1);
      renderGrid(box);
      expect(screen.getByRole("grid")).toHaveAccessibleDescription(
        "inventory:container.content.keyboard.selectionTips",
      );
    });
  });
});
