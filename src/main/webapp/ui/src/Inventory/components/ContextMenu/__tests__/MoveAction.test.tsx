import { test, describe, expect, beforeEach, vi } from 'vitest';
import React from "react";
import {
  render,
  screen,
} from "@testing-library/react";
import { action, observable } from "mobx";
import "@testing-library/jest-dom/vitest";
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
import userEvent from "@testing-library/user-event";

vi.mock("../../../Search/SearchView", () => ({
  default: vi.fn(() => <></>),
}));
vi.mock("@mui/material/Dialog", () => ({
  default: vi.fn(({ children }: { children: React.ReactNode }) => (
    <>{children}</>
  )),
}));

// this is because the Search component renders hidden "Cancel" buttons
vi.mock("../../../Search/Search", () => ({
  default: vi.fn(() => <></>),
}));

beforeEach(() => {
  vi.clearAllMocks();
});


describe("MoveAction", () => {
  test("After the dialog is closed, the overflow context menu should have been closed.", async () => {
    const user = userEvent.setup();
    const rootStore: StoreContainer = makeMockRootStore(
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

    const closeMenu = vi.fn(() => {});
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

    await user.click(screen.getAllByRole("button", { name: "Move" })[0]);
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(closeMenu).toHaveBeenCalled();
  });
});
