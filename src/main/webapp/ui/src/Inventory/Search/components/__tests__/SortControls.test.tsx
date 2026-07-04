import "@/stores/stores/RootStore";

import { ThemeProvider } from "@mui/material/styles";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import SearchContext from "../../../../stores/contexts/Search";
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";
import { sortProperties } from "../../../../stores/models/InventoryBaseRecord";
import Search from "../../../../stores/models/Search";
import materialTheme from "../../../../theme";
import { translateAdjustableTableLabel } from "../../../components/Tables/adjustableTableLabels";
import SortControls from "../SortControls";

describe("SortControls", () => {
  test("Current sort option should have aria-current attribute.", () => {
    const search = new Search({
      factory: mockFactory(),
    });
    render(
      <ThemeProvider theme={materialTheme}>
        <SearchContext.Provider
          value={{
            search,
            differentSearchForSettingActiveResult: search,
          }}
        >
          <SortControls />
        </SearchContext.Provider>
      </ThemeProvider>,
    );

    fireEvent.click(screen.getByRole("button", { name: "inventory:search.controls.sort.sortBy" }));
    const selectedOptions = sortProperties.filter(({ key }) => search.fetcher.isCurrentSort(key));
    if (selectedOptions.length !== 1) throw new Error("Invalid menu selection");

    const selectedOption = selectedOptions[0];
    const selectedLabel = translateAdjustableTableLabel(selectedOption.label, (key) => `inventory:${key}`);
    const sortDirectionLabel = search.fetcher.isOrderDesc
      ? "inventory:search.controls.sort.ascending"
      : "inventory:search.controls.sort.descending";
    expect(screen.getByRole("menuitem", { name: `${selectedLabel} ${sortDirectionLabel}` })).toHaveAttribute(
      "aria-current",
      "true",
    );
  });
});
