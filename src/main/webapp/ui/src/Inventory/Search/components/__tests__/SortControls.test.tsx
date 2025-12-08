/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import { ThemeProvider } from "@mui/material/styles";
import SearchContext from "../../../../stores/contexts/Search";
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";
import { sortProperties } from "../../../../stores/models/InventoryBaseRecord";
import Search from "../../../../stores/models/Search";
import materialTheme from "../../../../theme";
import SortControls from "../SortControls";

beforeEach(() => {
    jest.clearAllMocks();
});

afterEach(cleanup);

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

        fireEvent.click(screen.getByRole("button", { name: "Sort by" }));

        const selectedOptions = sortProperties.filter(({ key }) => search.fetcher.isCurrentSort(key));
        if (selectedOptions.length !== 1) throw new Error("Invalid menu selection");
        const selectedOption = selectedOptions[0];

        expect(
            screen.getByRole("menuitem", {
                name: new RegExp(`${selectedOption.label} (\\(A-Z\\)|\\(Z-A\\))`),
            }),
        ).toHaveAttribute("aria-current", "true");
    });
});
