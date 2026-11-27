import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { action, observable } from "mobx";
import type React from "react";
import { describe, expect, onTestFinished, test, vi } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";
import { makeMockContainer } from "../../../../stores/models/__tests__/ContainerModel/mocking";
import Search from "../../../../stores/models/Search";
import { makeMockRootStore } from "../../../../stores/stores/__tests__/RootStore/mocking";
import type { StoreContainer } from "../../../../stores/stores/RootStore";
import { storesContext } from "../../../../stores/stores-context";
import materialTheme from "../../../../theme";
import MoveDialog from "../../MoveToTarget/MoveDialog";
import MoveAction from "../MoveAction";
import "@/__tests__/__mocks__/matchMedia";

vi.mock("../../../Search/SearchView", () => ({
  default: vi.fn(() => <></>),
}));
vi.mock("@mui/material/Dialog", () => ({
  default: vi.fn(({ children }: { children: React.ReactNode }) => <>{children}</>),
}));
// this is because the Search component renders hidden "Cancel" buttons

vi.mock("../../../Search/Search", () => ({
  default: vi.fn(() => <></>),
}));
describe("MoveAction", () => {
  test("reports a failure to load children for a move", async () => {
    onTestFinished(silenceConsole(["error"], ["Could not load items to move"]));
    const user = userEvent.setup();
    const addAlert = vi.fn();
    const setIsMoving = vi.fn();
    const rootStore = makeMockRootStore({
      moveStore: {
        setIsMoving,
        setSelectedResults: vi.fn().mockRejectedValue(new Error("Children unavailable")),
      },
      uiStore: { addAlert },
    });
    render(
      <ThemeProvider theme={materialTheme}>
        <storesContext.Provider value={rootStore}>
          <MoveAction as="button" selectedResults={[makeMockContainer()]} disabled="" closeMenu={() => {}} />
        </storesContext.Provider>
      </ThemeProvider>,
    );

    await user.click(screen.getByRole("button", { name: "common:actions.move" }));

    expect(addAlert).toHaveBeenCalledWith(
      expect.objectContaining({ title: "inventory:moveToTarget.errors.moveFailed", variant: "error" }),
    );
    expect(setIsMoving).toHaveBeenLastCalledWith(false);
  });

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
          setSelectedResults: async () => {},
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
      }),
    );

    const closeMenu = vi.fn(() => {});
    render(
      <ThemeProvider theme={materialTheme}>
        <storesContext.Provider value={rootStore}>
          <MoveAction as="button" selectedResults={[makeMockContainer()]} disabled="" closeMenu={closeMenu} />
          <MoveDialog />
        </storesContext.Provider>
      </ThemeProvider>,
    );
    await user.click(screen.getAllByRole("button", { name: "common:actions.move" })[0]);

    await user.click(screen.getByRole("button", { name: "common:actions.cancel" }));
    expect(closeMenu).toHaveBeenCalled();
  });
});
