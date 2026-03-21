import { test, describe, vi, expect } from "vitest";
import "../../../../__mocks__/matchMedia";
import "../../../../__mocks__/resizeObserver";
import * as React from "react";
import {
  render,
  screen,
  within,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { storesContext } from "../../../stores/stores-context";
import { makeMockRootStore } from "../../../stores/stores/__tests__/RootStore/mocking";
import ResultsTable from "../ResultsTable";
import Search from "../../../stores/models/Search";
import SearchContext from "../../../stores/contexts/Search";
import { menuIDs } from "../../../util/menuIDs";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";
import { containerAttrs } from "../../../stores/models/__tests__/ContainerModel/mocking";
import { makeMockContainer } from "../../../stores/models/__tests__/ContainerModel/mocking";
import ApiServiceBase from "../../../common/ApiServiceBase";
import MemoisedFactory from "../../../stores/models/Factory/MemoisedFactory";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";

import { personAttrs } from "../../../stores/models/__tests__/PersonModel/mocking";

const REQUIRED_PERMISSIONS_TOOLTIP =
  "You do not have permission to select this item.";

const renderResultsTable = (search: Search) => {
  const rootStore = makeMockRootStore({
    uiStore: {
      isSmall: false,
      isVerySmall: false,
      isLarge: true,
      setVisiblePanel: vi.fn(),
    },
    searchStore: {
      search,
    },
  });

  return render(
    <ThemeProvider theme={materialTheme}>
      <storesContext.Provider value={rootStore}>
        <SearchContext.Provider
          value={{
            search,
            differentSearchForSettingActiveResult: search,
            scopedResult: null,
          }}
        >
          <ResultsTable contextMenuId={menuIDs.RESULTS} />
        </SearchContext.Provider>
      </storesContext.Provider>
    </ThemeProvider>,
  );
};

const setSearchResults = (search: Search, ...results: Array<InventoryRecord>) => {
  search.fetcher.setResults(results);
  search.fetcher.count = results.length;
};

describe("Results Table", () => {
  describe("Pagination", () => {
    test('When there are fewer items than the page size, the page size menu should show the count as "ALL"', async () => {
      const search = new Search({
        factory: new MemoisedFactory(),
      });

      vi.spyOn(ApiServiceBase.prototype, "query").mockImplementation(
        () =>
          Promise.resolve({
            data: {
              containers: [
                containerAttrs({
                  globalId: "IC0",
                  id: 0,
                  owner: personAttrs(),
                }),
                containerAttrs({
                  globalId: "IC1",
                  id: 1,
                  owner: personAttrs(),
                }),
                containerAttrs({
                  globalId: "IC2",
                  id: 2,
                  owner: personAttrs(),
                }),
                containerAttrs({
                  globalId: "IC3",
                  id: 3,
                  owner: personAttrs(),
                }),
                containerAttrs({
                  globalId: "IC4",
                  id: 4,
                  owner: personAttrs(),
                }),
                containerAttrs({
                  globalId: "IC5",
                  id: 5,
                  owner: personAttrs(),
                }),
              ],
            },
          }) as ReturnType<typeof ApiServiceBase.prototype.query>,
      );
      await waitFor(() => {
        void search.setupAndPerformInitialSearch({});

      });

      const rootStore = makeMockRootStore({});
      render(
        <ThemeProvider theme={materialTheme}>
          <storesContext.Provider value={rootStore}>
            <SearchContext.Provider
              value={{
                search,
                differentSearchForSettingActiveResult: search,
                scopedResult: null,
              }}
            >
              <ResultsTable contextMenuId={menuIDs.RESULTS} />
            </SearchContext.Provider>
          </storesContext.Provider>
        </ThemeProvider>,

      );
      within(screen.getByRole("navigation")).getByRole("combobox");
    });
  });

  describe("requiredPermissions", () => {
    test("disables selection and shows a tooltip for results that lack the required permissions", async () => {
      const user = userEvent.setup();
      const search = new Search({
        factory: new MemoisedFactory(),
        uiConfig: {
          requiredPermissions: ["UPDATE"],
        },
      });
      const readOnlyContainer = makeMockContainer({
        globalId: "IC9",
        id: 9,
        owner: personAttrs(),
        permittedActions: ["READ"],
      });

      setSearchResults(search, readOnlyContainer);
      renderResultsTable(search);

      const checkbox = screen.getByRole("checkbox", {
        name: /select result item/i,
      });
      expect(checkbox).toBeDisabled();

      const row = screen.getByText(readOnlyContainer.name).closest("tr");
      if (!row) throw new Error("Could not find table row for result");

      await user.hover(row);
      expect(
        await screen.findByText(REQUIRED_PERMISSIONS_TOOLTIP),
      ).toBeInTheDocument();
    });

    test("select all skips results that lack the required permissions", async () => {
      const user = userEvent.setup();
      const factory = new MemoisedFactory();
      const search = new Search({
        factory,
        uiConfig: {
          requiredPermissions: ["UPDATE"],
        },
      });
      const allowedContainer = makeMockContainer({
        globalId: "IC10",
        id: 10,
        owner: personAttrs(),
        permittedActions: ["READ", "UPDATE"],
      });
      const disallowedContainer = makeMockContainer({
        globalId: "IC11",
        id: 11,
        owner: personAttrs(),
        permittedActions: ["READ"],
      });

      setSearchResults(search, allowedContainer, disallowedContainer);
      renderResultsTable(search);

      await user.click(screen.getByRole("button", { name: /select all/i }));

      expect(allowedContainer.selected).toBe(true);
      expect(disallowedContainer.selected).toBe(false);
    });

    test("selection menu options keep disallowed results unselected", async () => {
      const user = userEvent.setup();
      const factory = new MemoisedFactory();
      const search = new Search({
        factory,
        uiConfig: {
          requiredPermissions: ["UPDATE"],
        },
      });
      const allowedContainer = makeMockContainer({
        globalId: "IC12",
        id: 12,
        owner: personAttrs(),
        permittedActions: ["READ", "UPDATE"],
      });
      const disallowedContainer = makeMockContainer({
        globalId: "IC13",
        id: 13,
        owner: personAttrs(),
        permittedActions: ["READ"],
      });

      allowedContainer.toggleSelected(true);
      disallowedContainer.toggleSelected(true);
      setSearchResults(search, allowedContainer, disallowedContainer);
      renderResultsTable(search);

      await user.click(screen.getByLabelText(/more selection options/i));
      await user.click(await screen.findByText("Invert"));

      expect(allowedContainer.selected).toBe(false);
      expect(disallowedContainer.selected).toBe(false);
    });

    test("shows alwaysFilteredOutReason instead of the requiredPermissions tooltip", async () => {
      const user = userEvent.setup();
      const search = new Search({
        factory: new MemoisedFactory(),
        uiConfig: {
          alwaysFilteredOutReason: "This item is already linked.",
          requiredPermissions: ["UPDATE"],
        },
      });
      const container = makeMockContainer({
        globalId: "IC14",
        id: 14,
        owner: personAttrs(),
        permittedActions: ["READ"],
      });
      search.alwaysFilterOut = () => true;

      setSearchResults(search, container);
      renderResultsTable(search);

      const row = screen.getByText(container.name).closest("tr");
      if (!row) throw new Error("Could not find table row for result");

      await user.hover(row);
      expect(
        await screen.findByText("This item is already linked."),
      ).toBeInTheDocument();
      expect(
        screen.queryByText(REQUIRED_PERMISSIONS_TOOLTIP),
      ).not.toBeInTheDocument();
    });
  });
});

