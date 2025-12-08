/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import "@testing-library/jest-dom";
import { ThemeProvider } from "@mui/material/styles";
import userEvent from "@testing-library/user-event";
import fc from "fast-check";
import { makeMockContainer } from "../../../../../stores/models/__tests__/ContainerModel/mocking";
import type ContainerModel from "../../../../../stores/models/ContainerModel";
import { makeMockRootStore } from "../../../../../stores/stores/__tests__/RootStore/mocking";
import type { StoreContainer } from "../../../../../stores/stores/RootStore";
import { storesContext } from "../../../../../stores/stores-context";
import materialTheme from "../../../../../theme";
import { assertNotNull } from "../../../../../util/__tests__/helpers";
import * as ArrayUtils from "../../../../../util/ArrayUtils";
import { parseInteger } from "../../../../../util/parsers";
import GridDimensions from "../GridDimensions";

function makeRootStoreWithGridContainer(): {
    rootStore: StoreContainer;
    gridContainer: ContainerModel;
} {
    const activeResult = makeMockContainer({
        id: null,
        cType: "GRID",
        gridLayout: {
            columnsNumber: 1,
            rowsNumber: 1,
            columnsLabelType: "ABC",
            rowsLabelType: "N123",
        },
    });
    const rootStore = makeMockRootStore({
        searchStore: {
            activeResult,
        },
    });
    return { rootStore, gridContainer: activeResult };
}

beforeEach(() => {
    jest.clearAllMocks();
});

afterEach(cleanup);

describe("GridDimensions", () => {
    test("Each of the standard dimension menu options sets the rows and columns to a valid number.", async () => {
        const user = userEvent.setup();
        const { rootStore } = makeRootStoreWithGridContainer();
        render(
            <ThemeProvider theme={materialTheme}>
                <storesContext.Provider value={rootStore}>
                    <GridDimensions />
                </storesContext.Provider>
            </ThemeProvider>,
        );

        // get list of all menu options by opening and closing menu
        fireEvent.mouseDown(screen.getByRole("combobox"));
        const menuOptions = within(screen.getByRole("listbox"))
            .getAllByRole("option")
            .map((o) => o.textContent || "");
        await user.click(
            within(screen.getByRole("listbox")).getByRole("option", {
                name: "Custom",
            }),
        );

        // for each menu option, assert it sets the rows and cols to valid values
        for (const option of menuOptions) {
            fireEvent.mouseDown(screen.getByRole("combobox"));
            await user.click(
                within(screen.getByRole("listbox")).getByRole("option", {
                    name: option,
                }),
            );

            const rowsEl: HTMLInputElement = screen.getByRole("spinbutton", {
                name: "rows",
            });
            const rows = parseInteger(rowsEl.value).orElse(null);
            expect(rows).not.toBeNull();
            expect(rows).toBeGreaterThanOrEqual(2);
            expect(rows).toBeLessThanOrEqual(24);

            const columnsEl: HTMLInputElement = screen.getByRole("spinbutton", {
                name: "rows",
            });
            const columns = parseInteger(columnsEl.value).orElse(null);
            expect(columns).not.toBeNull();
            expect(columns).toBeGreaterThanOrEqual(2);
            expect(columns).toBeLessThanOrEqual(24);
        }
    });

    test("Choosing custom should not change the dimensions.", async () => {
        const user = userEvent.setup();
        const { rootStore } = makeRootStoreWithGridContainer();
        render(
            <ThemeProvider theme={materialTheme}>
                <storesContext.Provider value={rootStore}>
                    <GridDimensions />
                </storesContext.Provider>
            </ThemeProvider>,
        );

        // get the first menu option that is not "Custom"
        fireEvent.mouseDown(screen.getByRole("combobox"));
        const menuOption = assertNotNull(
            ArrayUtils.head(
                within(screen.getByRole("listbox"))
                    .getAllByRole("option")
                    .map((o) => o.textContent || "")
                    .filter((o) => o !== "Custom"),
            ).orElse(null),
        );

        // tap that menu option, setting the rows and columns
        await user.click(
            within(screen.getByRole("listbox")).getByRole("option", {
                name: menuOption,
            }),
        );
        const rowsBeforeEl: HTMLInputElement = screen.getByRole("spinbutton", {
            name: "rows",
        });
        const rowsBefore = parseInteger(rowsBeforeEl.value).orElse(null);
        expect(rowsBefore).not.toBeNull();

        const columnsBeforeEl: HTMLInputElement = screen.getByRole("spinbutton", {
            name: "columns",
        });
        const columnsBefore = parseInteger(columnsBeforeEl.value).orElse(null);
        expect(columnsBefore).not.toBeNull();

        // choose "custom"
        fireEvent.mouseDown(screen.getByRole("combobox"));
        await user.click(
            within(screen.getByRole("listbox")).getByRole("option", {
                name: "Custom",
            }),
        );

        // assert that the values have not changed
        const rowsAfterEl: HTMLInputElement = screen.getByRole("spinbutton", {
            name: "rows",
        });
        const rowsAfter = parseInteger(rowsAfterEl.value).orElse(null);
        expect(rowsAfter).not.toBeNull();

        const columnsAfterEl: HTMLInputElement = screen.getByRole("spinbutton", {
            name: "columns",
        });
        const columnsAfter = parseInteger(columnsAfterEl.value).orElse(null);
        expect(columnsAfter).not.toBeNull();
        expect(rowsAfter).toEqual(rowsBefore);
        expect(columnsBefore).toEqual(columnsAfter);
    });

    test("Changing rows sets menu to Custom", async () => {
        const user = userEvent.setup();
        const { rootStore } = makeRootStoreWithGridContainer();
        render(
            <ThemeProvider theme={materialTheme}>
                <storesContext.Provider value={rootStore}>
                    <GridDimensions />
                </storesContext.Provider>
            </ThemeProvider>,
        );

        // set the first menu option that is not "Custom"
        fireEvent.mouseDown(screen.getByRole("combobox"));
        const menuOption = assertNotNull(
            ArrayUtils.head(
                within(screen.getByRole("listbox"))
                    .getAllByRole("option")
                    .map((o) => o.textContent || "")
                    .filter((o) => o !== "Custom"),
            ).orElse(null),
        );
        expect(menuOption).not.toBeNull();
        await user.click(
            within(screen.getByRole("listbox")).getByRole("option", {
                name: menuOption,
            }),
        );

        const rowsBeforeEl: HTMLInputElement = screen.getByRole("spinbutton", {
            name: "rows",
        });
        // change the rows
        const rowsBefore = parseInteger(rowsBeforeEl.value).orElse(null);
        const newRows = (assertNotNull(rowsBefore) + 1) % 24;
        fireEvent.input(screen.getByRole("spinbutton", { name: "rows" }), {
            target: { value: newRows },
        });

        // assert that the menu is custom
        expect(screen.getByRole("combobox")).toBeVisible();
    });

    test("Changing columns sets menu to Custom", async () => {
        const user = userEvent.setup();
        const { rootStore } = makeRootStoreWithGridContainer();
        render(
            <ThemeProvider theme={materialTheme}>
                <storesContext.Provider value={rootStore}>
                    <GridDimensions />
                </storesContext.Provider>
            </ThemeProvider>,
        );

        // set the first menu option that is not "Custom"
        fireEvent.mouseDown(screen.getByRole("combobox"));
        const menuOption = assertNotNull(
            ArrayUtils.head(
                within(screen.getByRole("listbox"))
                    .getAllByRole("option")
                    .map((o) => o.textContent)
                    .filter((o) => o !== "Custom"),
            ).orElse(null),
        );
        expect(menuOption).not.toBeNull();
        await user.click(
            within(screen.getByRole("listbox")).getByRole("option", {
                name: menuOption,
            }),
        );

        // change the columns
        const columnsBeforeEl: HTMLInputElement = screen.getByRole("spinbutton", {
            name: "columns",
        });
        const columnsBefore = parseInteger(columnsBeforeEl.value).orElse(null);
        // expect(columnsBefore).not.toBeNull();
        const newColumns = (assertNotNull(columnsBefore) + 1) % 24;
        fireEvent.input(screen.getByRole("spinbutton", { name: "columns" }), {
            target: { value: newColumns },
        });

        // assert that the menu is custom
        expect(screen.getByRole("combobox")).toBeVisible();
    });

    test("The multiplication of the rows and columns should equal the size quoted in the menu item.", async () => {
        const user = userEvent.setup();
        const { rootStore } = makeRootStoreWithGridContainer();
        render(
            <ThemeProvider theme={materialTheme}>
                <storesContext.Provider value={rootStore}>
                    <GridDimensions />
                </storesContext.Provider>
            </ThemeProvider>,
        );

        // get list of all menu options by opening and closing menu
        fireEvent.mouseDown(screen.getByRole("combobox"));
        const menuOptions = within(screen.getByRole("listbox"))
            .getAllByRole("option")
            .map((o) => o.textContent || "")
            .filter((o) => o !== "Custom");
        await user.click(
            within(screen.getByRole("listbox")).getByRole("option", {
                name: "Custom",
            }),
        );

        // for each menu option, assert that the rows and colunmns, when multiplied, are the number quoted by the option
        for (const option of menuOptions) {
            fireEvent.mouseDown(screen.getByRole("combobox"));
            await user.click(
                within(screen.getByRole("listbox")).getByRole("option", {
                    name: option,
                }),
            );

            const rowsEl: HTMLInputElement = screen.getByRole("spinbutton", {
                name: "rows",
            });
            const rows = parseInteger(rowsEl.value).orElse(null);

            const columnsEl: HTMLInputElement = screen.getByRole("spinbutton", {
                name: "columns",
            });
            const columns = parseInteger(columnsEl.value).orElse(null);

            expect(option).toMatch(new RegExp(`${assertNotNull(rows) * assertNotNull(columns)}`));
        }
    });

    test("The first option, the most popular, should be 96-well plate.", () => {
        const { rootStore } = makeRootStoreWithGridContainer();
        render(
            <ThemeProvider theme={materialTheme}>
                <storesContext.Provider value={rootStore}>
                    <GridDimensions />
                </storesContext.Provider>
            </ThemeProvider>,
        );

        fireEvent.mouseDown(screen.getByRole("combobox"));
        const menuOption = ArrayUtils.head(
            within(screen.getByRole("listbox"))
                .getAllByRole("option")
                .map((o) => o.textContent || ""),
        ).orElse(null);
        expect(assertNotNull(menuOption)).toMatch(/96 well plate/);
    });

    test("Selecting 96-well should save cols: 12 and rows: 8.", async () => {
        const user = userEvent.setup();
        const { rootStore, gridContainer } = makeRootStoreWithGridContainer();
        const spy = jest.spyOn(gridContainer, "setAttributesDirty");
        render(
            <ThemeProvider theme={materialTheme}>
                <storesContext.Provider value={rootStore}>
                    <GridDimensions />
                </storesContext.Provider>
            </ThemeProvider>,
        );

        fireEvent.mouseDown(screen.getByRole("combobox"));

        await user.click(
            within(screen.getByRole("listbox")).getByRole("option", {
                name: "96 well plate",
            }),
        );

        expect(spy).toHaveBeenCalledWith({
            gridLayout: expect.objectContaining({ columnsNumber: 12, rowsNumber: 8 }),
        });
    });

    test("When a menu option is chosen, the same values should be passed to setAttributesDirty as displayed in the numerical fields.", async () => {
        const user = userEvent.setup();
        fc.assert(
            fc.asyncProperty(fc.nat(), async (unboundedIndex) => {
                cleanup();
                const { rootStore, gridContainer } = makeRootStoreWithGridContainer();
                render(
                    <ThemeProvider theme={materialTheme}>
                        <storesContext.Provider value={rootStore}>
                            <GridDimensions />
                        </storesContext.Provider>
                    </ThemeProvider>,
                );

                // get list of all menu options by opening and closing menu
                fireEvent.mouseDown(screen.getByRole("combobox"));
                const menuOptions = within(screen.getByRole("listbox"))
                    .getAllByRole("option")
                    .map((o) => o.textContent || "")
                    .filter((o) => o !== "Custom");

                const option = menuOptions[unboundedIndex % menuOptions.length];

                let gridLayout: any;
                jest.spyOn(gridContainer, "setAttributesDirty").mockImplementation((args: any) => {
                    gridLayout = args.gridLayout;
                });

                await user.click(
                    within(screen.getByRole("listbox")).getByRole("option", {
                        name: option,
                    }),
                );

                const rowsEl: HTMLInputElement = screen.getByRole("spinbutton", {
                    name: "rows",
                });
                const rows = parseInteger(rowsEl.value).orElse(null);
                expect(rows).not.toBeNull();

                const columnsEl: HTMLInputElement = screen.getByRole("spinbutton", {
                    name: "columns",
                });
                const columns = parseInteger(columnsEl.value).orElse(null);
                expect(columns).not.toBeNull();

                expect(rows).toEqual(gridLayout?.rowsNumber);
                expect(columns).toEqual(gridLayout?.columnsNumber);
            }),
            { numRuns: 1 },
        );
    });
});
