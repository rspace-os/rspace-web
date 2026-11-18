import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import { runInAction } from "mobx";
import { describe, expect, test, vi } from "vitest";
import NavigateContext from "../../../../stores/contexts/Navigate";
import SearchContext from "../../../../stores/contexts/Search";
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";
import Search from "../../../../stores/models/Search";
import { makeMockRootStore } from "../../../../stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "../../../../stores/stores-context";
import materialTheme from "../../../../theme";
import SearchFeedback from "../SearchFeedback";

vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({ authStore: { isSynchronizing: false } }),
}));

const renderFeedback = (search: Search) =>
  render(
    <ThemeProvider theme={materialTheme}>
      <storesContext.Provider value={makeMockRootStore({ searchStore: { savedSearches: [], savedBaskets: [] } })}>
        <NavigateContext.Provider
          value={{
            useNavigate: () => () => {},
            useLocation: () => ({ search: "", pathname: "", hash: "", state: null, key: "" }),
          }}
        >
          <SearchContext.Provider value={{ search, differentSearchForSettingActiveResult: search }}>
            <SearchFeedback />
          </SearchContext.Provider>
        </NavigateContext.Provider>
      </storesContext.Provider>
    </ThemeProvider>,
  );

const structureSearch = () => {
  const search = new Search({ factory: mockFactory() });
  search.setChemicalFilter({ chemQuery: "c1ccccc1", chemSearchType: "SUBSTRUCTURE" }, false);
  return search;
};

describe("SearchFeedback for structure search", () => {
  test("reports the number of structure search results", () => {
    renderFeedback(structureSearch());

    expect(screen.getByRole("status", { name: "inventory:search.feedback.label" })).toHaveTextContent(
      "inventory:search.feedback.results",
    );
  });

  test("warns when the text query hit the server's limit", () => {
    const search = structureSearch();
    runInAction(() => {
      search.fetcher.textSearchLimitReached = true;
    });
    renderFeedback(search);

    expect(screen.getByRole("status", { name: "inventory:search.feedback.label" })).toMatchTextContent(
      "inventory:search.feedback.textSearchLimitReached",
    );
  });

  test("shows a failed structure search instead of the result count", () => {
    const search = structureSearch();
    runInAction(() => {
      search.fetcher.error = "Structure search is not available. Remove the chemical structure filter.";
    });
    renderFeedback(search);

    const status = screen.getByRole("status", { name: "inventory:search.feedback.label" });
    expect(status).toHaveTextContent("Structure search is not available. Remove the chemical structure filter.");
  });
});
