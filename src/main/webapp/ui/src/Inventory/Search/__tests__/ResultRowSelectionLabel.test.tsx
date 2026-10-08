import "@/stores/stores/RootStore";
import { describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/resizeObserver";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import { setupRealAppI18n } from "@/__tests__/helpers/realI18n";
import SearchContext from "../../../stores/contexts/Search";
import { makeMockContainer } from "../../../stores/models/__tests__/ContainerModel/mocking";
import { personAttrs } from "../../../stores/models/__tests__/PersonModel/mocking";
import MemoisedFactory from "../../../stores/models/Factory/MemoisedFactory";
import Search from "../../../stores/models/Search";
import { makeMockRootStore } from "../../../stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "../../../stores/stores-context";
import materialTheme from "../../../theme";
import { menuIDs } from "../../../util/menuIDs";
import ResultsTable from "../ResultsTable";

setupRealAppI18n();

function renderResults(selectionMode: "MULTIPLE" | "SINGLE") {
  const search = new Search({ factory: new MemoisedFactory(), uiConfig: { selectionMode } });
  const container = makeMockContainer({ globalId: "IC7", id: 7, name: "Freezer rack A", owner: personAttrs() });
  search.fetcher.setResults([container]);
  search.fetcher.count = 1;
  const rootStore = makeMockRootStore({
    uiStore: { isSmall: false, isVerySmall: false, isLarge: true, setVisiblePanel: vi.fn() },
    searchStore: { search },
  });
  render(
    <ThemeProvider theme={materialTheme}>
      <storesContext.Provider value={rootStore}>
        <SearchContext.Provider value={{ search, differentSearchForSettingActiveResult: search, scopedResult: null }}>
          <ResultsTable contextMenuId={menuIDs.RESULTS} />
        </SearchContext.Provider>
      </storesContext.Provider>
    </ThemeProvider>,
  );
}

describe("result row selection controls", () => {
  test("name the item in the checkbox label", () => {
    renderResults("MULTIPLE");
    expect(screen.getByRole("checkbox", { name: "Select Freezer rack A" })).toBeInTheDocument();
    expect(screen.queryByRole("checkbox", { name: /\{name\}/ })).not.toBeInTheDocument();
  });

  test("name the item in the radio label", () => {
    renderResults("SINGLE");
    expect(screen.getByRole("radio", { name: "Select Freezer rack A" })).toBeInTheDocument();
  });
});
