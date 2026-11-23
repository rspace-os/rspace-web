import "@/stores/stores/RootStore";

import { ThemeProvider } from "@mui/material/styles";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test } from "vitest";
import { setupRealAppI18n } from "@/__tests__/helpers/realI18n";
import SearchContext from "../../../../stores/contexts/Search";
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";
import type { DeletedItems } from "../../../../stores/definitions/Search";
import Search from "../../../../stores/models/Search";
import { makeMockRootStore } from "../../../../stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "../../../../stores/stores-context";
import materialTheme from "../../../../theme";
import SearchParameterChips from "../SearchParameterChips";

function renderPermalinkChips(deletedItems: DeletedItems) {
  const search = new Search({
    factory: mockFactory(),
    fetcherParams: { permalink: { type: "sample", id: 21, version: null }, deletedItems },
  });
  // mirrors SearchRouter: the main search navigates to a URL built by generateQuery
  const navigatedTo: Array<string> = [];
  search.overrideSearchOnFilter = (args) => {
    navigatedTo.push(`/inventory/search?${search.fetcher.generateQuery(args).toString()}`);
  };
  const rootStore = makeMockRootStore({
    searchStore: { savedBaskets: [], getBaskets: () => Promise.resolve() },
  });
  render(
    <ThemeProvider theme={materialTheme}>
      <storesContext.Provider value={rootStore}>
        <SearchContext.Provider value={{ search, differentSearchForSettingActiveResult: search }}>
          <SearchParameterChips />
        </SearchContext.Provider>
      </storesContext.Provider>
    </ThemeProvider>,
  );
  return navigatedTo;
}

describe("SearchParameterChips", () => {
  setupRealAppI18n();

  test.each<DeletedItems>(["EXCLUDE", "DELETED_ONLY"])(
    "clearing the Global ID chip of a permalink (deletedItems %s) navigates to the unscoped list",
    async (deletedItems) => {
      const user = userEvent.setup();
      const navigatedTo = renderPermalinkChips(deletedItems);
      const chip = screen.getByRole("button", { name: "Global ID: SA21" });
      await user.click(within(chip).getByTestId("CancelIcon"));
      expect(navigatedTo).toEqual(["/inventory/search?"]);
    },
  );
});
