/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import {
  cleanup,
  screen,
  waitFor,
  act,
  fireEvent,
} from "@testing-library/react";
import { action, observable } from "mobx";
import "@testing-library/jest-dom";
import { storesContext } from "../../../../stores/stores-context";
import { makeMockRootStore } from "../../../../stores/stores/__tests__/RootStore/mocking";
import MoveDialog from "../MoveDialog";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";
import Search from "../../../../stores/models/Search";
import Dialog from "@mui/material/Dialog";
import "__mocks__/matchMedia";
import {
  makeMockSubSample,
  subSampleAttrsArbitrary,
} from "../../../../stores/models/__tests__/SubSampleModel/mocking";
import { render, within } from "../../../../__tests__/customQueries";
import fc from "fast-check";
import * as ArrayUtils from "../../../../util/ArrayUtils";
import { type StoreContainer } from "../../../../stores/stores/RootStore";
import SubSampleModel from "../../../../stores/models/SubSampleModel";

jest.mock("../../../Search/SearchView", () => jest.fn(() => <></>));
jest.mock("@mui/material/Dialog", () =>
  jest.fn(({ children }) => <>{children}</>)
);
jest.mock("../../../../components/Inputs/DateField", () => <></>);
jest.mock("../../../../components/Inputs/TimeField", () => <></>);

// this is because the Search component renders hidden "Cancel" buttons
jest.mock("../../../Search/Search", () => jest.fn(() => <></>));

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("MoveDialog", () => {
  test("When cancel is pressed, the dialog should close.", async () => {
    let rootStore: StoreContainer;
    rootStore = makeMockRootStore(
      observable({
        moveStore: {
          isMoving: true,
          selectedResults: [],
          setIsMoving: action((x: boolean) => {
            rootStore.moveStore.isMoving = x;
          }),
          search: new Search({
            factory: mockFactory(),
          }),
          submitting: "NO",
        },
        uiStore: {
          setDialogVisiblePanel: () => {},
          isSingleColumnLayout: false,
        },
        searchStore: {
          savedSearches: [],
          savedBaskets: [],
          getBaskets: () => {},
        },
      })
    );

    render(
      <ThemeProvider theme={materialTheme}>
        <storesContext.Provider value={rootStore}>
          <MoveDialog />
        </storesContext.Provider>
      </ThemeProvider>
    );

    expect(Dialog).toHaveBeenCalledWith(
      expect.objectContaining({ open: true }),
      expect.anything()
    );

    act(() => {
      screen.getByRole("button", { name: "Cancel" }).click();
    });

    await waitFor(() => {
      expect(rootStore.moveStore.isMoving).toBe(false);
    });
    expect(Dialog).toHaveBeenCalledWith(
      expect.objectContaining({ open: false }),
      expect.anything()
    );
  });

  test("Table hidden in header should list all selectedResults", () => {
    fc.assert(
      fc
        .property(
          fc.array<SubSampleModel>(
            subSampleAttrsArbitrary.map((attrs) => makeMockSubSample(attrs))
          ),
          (selectedResults) => {
            // this check prevents the non-unique react key warning
            fc.pre(
              ArrayUtils.allAreUnique(selectedResults.map((r) => r.globalId))
            );

            let rootStore: StoreContainer;
            rootStore = makeMockRootStore(
              observable({
                moveStore: {
                  isMoving: true,
                  selectedResults,
                  setIsMoving: action((x: boolean) => {
                    rootStore.moveStore.isMoving = x;
                  }),
                  search: new Search({
                    factory: mockFactory(),
                  }),
                  submitting: "NO",
                },
                uiStore: {
                  setDialogVisiblePanel: () => {},
                  isSingleColumnLayout: false,
                },
                searchStore: {
                  savedSearches: [],
                  savedBaskets: [],
                  getBaskets: () => {},
                },
              })
            );

            render(
              <ThemeProvider theme={materialTheme}>
                <storesContext.Provider value={rootStore}>
                  <MoveDialog />
                </storesContext.Provider>
              </ThemeProvider>
            );

            fireEvent.click(
              screen.getByRole("button", { name: "Show items being moved" })
            );

            expect(
              within(screen.getByRole("table")).getAllByRole("row").length
            ).toBe(selectedResults.length + 1);

            const table = screen.getByRole("table");
            const [headerRow, ...bodyRows] = within(table).getAllByRole("row");
            const indexOfNameColumn =
              within(headerRow).getIndexOfTableCell("Name");
            const allNameCells = bodyRows.map(
              (row) => within(row).getAllByRole("cell")[indexOfNameColumn]
            );

            expect(
              selectedResults.every((r) =>
                allNameCells.some((cell) => cell.textContent === r.name)
              )
            ).toBe(true);
          }
        )
        .afterEach(cleanup),
      { numRuns: 1 }
    );
  });
});
