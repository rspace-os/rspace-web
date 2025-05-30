/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import {
  render,
  cleanup,
  screen,
  fireEvent,
  within,
  act,
} from "@testing-library/react";
import "@testing-library/jest-dom";
import SearchContext from "../../../../stores/contexts/Search";
import materialTheme from "../../../../theme";
import { ThemeProvider } from "@mui/material/styles";
import Searchbar from "../Searchbar";
import Search from "../../../../stores/models/Search";
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";
import "__mocks__/resizeObserver";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("Searchbar", () => {
  test("If lots of text is entered then the expanded field dialog it available.", () => {
    const handleSearch = jest.fn<[string], void>();
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
      </ThemeProvider>
    );

    fireEvent.change(screen.getByRole("searchbox", { name: "Search" }), {
      target: { value: "this is a really long piece of text" },
    });

    expect(screen.getByRole("button", { name: "Expand field" })).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Expand field" }));

    fireEvent.click(
      within(screen.getByRole("dialog")).getByRole("button", { name: "Search" })
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
      </ThemeProvider>
    );

    act(() => {
      search.fetcher.setAttributes({
        query: "foo",
      });
    });

    expect(screen.getByRole("searchbox")).toHaveValue("foo");
  });
});
