import "@/stores/stores/RootStore";
import "@/__tests__/__mocks__/resizeObserver";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import SearchContext from "@/stores/contexts/Search";
import { containerAttrs, makeMockContainer } from "@/stores/models/__tests__/ContainerModel/mocking";
import type ContainerModel from "@/stores/models/ContainerModel";
import { prepareContainer } from "@/Inventory/components/Operations/placement";
import materialTheme from "@/theme";
import ContentGrid from "../ContentGrid";

/** A 2x2 grid box with A1 holding a rack. */
const boxWithA1Taken = () =>
  makeMockContainer({
    id: 1,
    globalId: "IC1",
    name: "Box",
    cType: "GRID",
    gridLayout: { columnsNumber: 2, rowsNumber: 2, columnsLabelType: "N123", rowsLabelType: "ABC" },
    locationsCount: 4,
    contentSummary: { totalCount: 1, subSampleCount: 0, containerCount: 1, instrumentCount: 0 },
    locations: [
      {
        id: 11,
        coordX: 1,
        coordY: 1,
        content: containerAttrs({ id: 9, globalId: "IC9", name: "Rack" }),
      },
    ],
  });

function renderGrid(box: ContainerModel) {
  render(
    <ThemeProvider theme={materialTheme}>
      <SearchContext.Provider
        value={{ search: box.contentSearch, scopedResult: box, differentSearchForSettingActiveResult: box.contentSearch }}
      >
        <ContentGrid />
      </SearchContext.Provider>
    </ThemeProvider>,
  );
}

const draggableIn = (cell: HTMLElement) => within(cell).getByRole("button");

describe("ContentGrid", () => {
  describe("drag-and-drop", () => {
    it("lets the user drag an item to another location in the container's own grid", () => {
      const box = boxWithA1Taken();
      renderGrid(box);
      expect(draggableIn(screen.getAllByRole("cell")[0])).toHaveAttribute("aria-disabled", "false");
    });

    it("is off in a grid used only to choose locations for new records", () => {
      const box = boxWithA1Taken();
      prepareContainer(box, 1);
      renderGrid(box);
      expect(draggableIn(screen.getAllByRole("cell")[0])).toHaveAttribute("aria-disabled", "true");
    });

    it("cannot be started from the keyboard in such a grid, so the arrow keys keep moving the selection", async () => {
      const user = userEvent.setup();
      const box = boxWithA1Taken();
      prepareContainer(box, 1);
      renderGrid(box);
      await user.tab();
      await user.keyboard(" {ArrowRight}");
      expect(screen.getAllByRole("cell")[1]).toHaveAttribute("aria-selected", "true");
    });
  });
});
