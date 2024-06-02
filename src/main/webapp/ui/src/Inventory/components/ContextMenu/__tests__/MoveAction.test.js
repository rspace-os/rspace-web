/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, act } from "@testing-library/react";
import { action, observable } from "mobx";
import "@testing-library/jest-dom";
import { storesContext } from "../../../../stores/stores-context";
import { makeMockRootStore } from "../../../../stores/stores/__tests__/RootStore/mocking";
import { makeMockContainer } from "../../../../stores/models/__tests__/ContainerModel/mocking";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";
import Search from "../../../../stores/models/Search";
import MoveAction from "../MoveAction";
import MoveDialog from "../../MoveToTarget/MoveDialog";
import "__mocks__/matchMedia";
import { type StoreContainer } from "../../../../stores/stores/RootStore";

jest.mock("../../../Search/SearchView", () => jest.fn(() => <></>));
jest.mock("@mui/material/Dialog", () =>
  jest.fn(({ children }) => <>{children}</>)
);

// this is because the Search component renders hidden "Cancel" buttons
jest.mock("../../../Search/Search", () => jest.fn(() => <></>));

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("MoveAction", () => {
  test("After the dialog is closed, the overflow context menu should have been closed.", () => {
    let rootStore: StoreContainer;
    rootStore = makeMockRootStore(
      observable({
        moveStore: {
          isMoving: false,
          selectedResults: [],
          setIsMoving: action((x: boolean) => {
            rootStore.moveStore.isMoving = x;
          }),
          search: new Search({
            factory: mockFactory(),
          }),
          submitting: "NO",
          setSelectedResults: () => {},
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

    const closeMenu = jest.fn(() => {});
    render(
      <ThemeProvider theme={materialTheme}>
        <storesContext.Provider value={rootStore}>
          <MoveAction
            as="button"
            selectedResults={[makeMockContainer()]}
            disabled=""
            closeMenu={closeMenu}
          />
          <MoveDialog />
        </storesContext.Provider>
      </ThemeProvider>
    );

    act(() => {
      screen.getAllByRole("button", { name: "Move" })[0].click();
    });
    act(() => {
      screen.getByRole("button", { name: "Cancel" }).click();
    });

    expect(closeMenu).toHaveBeenCalled();
  });
});
