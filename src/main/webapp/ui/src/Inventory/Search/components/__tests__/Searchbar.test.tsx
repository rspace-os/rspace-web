import "@/stores/stores/RootStore";
import { ThemeProvider } from "@mui/material/styles";
import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import SearchContext from "../../../../stores/contexts/Search";
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";
import Search from "../../../../stores/models/Search";
import materialTheme from "../../../../theme";
import Searchbar from "../Searchbar";

import "@/__tests__/__mocks__/resizeObserver";

describe("Searchbar", () => {
  test("If lots of text is entered then the expanded field dialog it available.", async () => {
    const user = userEvent.setup();
    const handleSearch = vi.fn<(query: string) => void>();
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
          <Searchbar handleSearch={handleSearch} />
        </SearchContext.Provider>
      </ThemeProvider>,
    );
    await user.type(
      screen.getByRole("searchbox", { name: "inventory:search.controls.searchbar.scanPlaceholder" }),
      "this is a really long piece of text",
    );
    expect(screen.getByRole("button", { name: "common:searchDialog.expandField" })).toBeVisible();

    await user.click(screen.getByRole("button", { name: "common:searchDialog.expandField" }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "common:actions.search" }));
    expect(handleSearch).toHaveBeenCalled();
  });
  test("When the query search parameter changes, the new value should be shown.", () => {
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
          <Searchbar handleSearch={() => {}} />
        </SearchContext.Provider>
      </ThemeProvider>,
    );
    act(() => {
      search.fetcher.setAttributes({
        query: "foo",
      });
    });
    expect(screen.getByRole("searchbox")).toHaveValue("foo");
  });
});
