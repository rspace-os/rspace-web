import userEvent from "@testing-library/user-event";
import { replaceValue } from "@/__tests__/helpers/userInteractions";
import { test, describe, expect, vi } from "vitest";
import React from "react";
import { render, screen, within, act } from "@testing-library/react";
import SearchContext from "../../../../stores/contexts/Search";
import materialTheme from "../../../../theme";
import { ThemeProvider } from "@mui/material/styles";
import Searchbar from "../Searchbar";
import Search from "../../../../stores/models/Search";
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";
import "@/__tests__/__mocks__/resizeObserver";
describe("Searchbar", () => {
  test("If lots of text is entered then the expanded field dialog it available.", async () => {
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
    await replaceValue(
      screen.getByRole("searchbox", {
        name: "Search",
      }),
      "this is a really long piece of text",
    );
    expect(
      screen.getByRole("button", {
        name: "Expand field",
      }),
    ).toBeVisible();
    await userEvent.click(
      screen.getByRole("button", {
        name: "Expand field",
      }),
    );
    await userEvent.click(
      within(screen.getByRole("dialog")).getByRole("button", {
        name: "Search",
      }),
    );
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
