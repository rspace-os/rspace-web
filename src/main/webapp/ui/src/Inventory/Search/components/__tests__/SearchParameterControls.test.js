/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, act } from "@testing-library/react";
import "@testing-library/jest-dom";
import { makeMockRootStore } from "../../../../stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "../../../../stores/stores-context";
import Search from "../../../../stores/models/Search";
import SearchContext from "../../../../stores/contexts/Search";
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";
import SearchParameterControls from "../SearchParameterControls";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import "../../../../../__mocks__/matchMedia.js";

jest.mock("../../../../common/InvApiService", () => {});
jest.mock("../../../../stores/stores/RootStore", () => () => ({
  searchStore: {
    search: null,
    savedSearches: [{ name: "Test search", resultType: "SAMPLE" }],
    savedBaskets: [],
  },
}));

window.fetch = jest.fn(() =>
  Promise.resolve({
    status: 200,
    ok: true,
    json: () => Promise.resolve(),
  })
);


beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("SearchParameterControls", () => {
  describe("Saved searches controls", () => {
    test("If the search disallows a particular record type, saved searches with that type filter should be disabled.", () => {
      const rootStore = makeMockRootStore({
        searchStore: {
          savedSearches: [{ name: "Test search", resultType: "SAMPLE" }],
          savedBaskets: [],
        },
      });

      const search = new Search({
        factory: mockFactory(),
        uiConfig: {
          allowedTypeFilters: new Set(["CONTAINER"]),
        },
      });

      render(
        <ThemeProvider theme={materialTheme}>
          <storesContext.Provider value={rootStore}>
            <SearchContext.Provider
              value={{
                search,
                differentSearchForSettingActiveResult: search,
              }}
            >
              <SearchParameterControls />
            </SearchContext.Provider>
          </storesContext.Provider>
        </ThemeProvider>
      );

      act(() => {
        screen.getByRole("button", { name: "Saved Searches" }).click();
      });
      expect(
        screen.getByRole("menuitem", { name: /^Test search/ })
      ).toHaveAttribute("aria-disabled", "true");
    });
  });
});
