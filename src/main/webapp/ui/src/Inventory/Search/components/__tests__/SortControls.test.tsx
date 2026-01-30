/*
 */
import {
  describe,
  test,
  expect,
  vi,
  beforeEach,
} from "vitest";
import React from "react";
import {
  render,
  screen,
  fireEvent,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import SortControls from "../SortControls";
import { sortProperties } from "../../../../stores/models/InventoryBaseRecord";
import SearchContext from "../../../../stores/contexts/Search";
import Search from "../../../../stores/models/Search";
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";
import materialTheme from "../../../../theme";
import { ThemeProvider } from "@mui/material/styles";

beforeEach(() => {
  vi.clearAllMocks();
});


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
      </ThemeProvider>
    );

    fireEvent.click(screen.getByRole("button", { name: "Sort by" }));

    const selectedOptions = sortProperties.filter(({ key }) =>
      search.fetcher.isCurrentSort(key)
    );
    if (selectedOptions.length !== 1) throw new Error("Invalid menu selection");
    const selectedOption = selectedOptions[0];

    expect(
      screen.getByRole("menuitem", {
        name: new RegExp(`${selectedOption.label} (\\(A-Z\\)|\\(Z-A\\))`),
      })
    ).toHaveAttribute("aria-current", "true");
  });
});


