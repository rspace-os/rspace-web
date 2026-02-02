import { test, describe, expect, beforeEach, vi } from 'vitest';
import React from "react";
import {
  render,
  waitFor,
  screen,
  fireEvent,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { makeMockContainer } from "../../../../stores/models/__tests__/ContainerModel/mocking";
import EditAction from "../EditAction";
import { makeMockRootStore } from "../../../../stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "../../../../stores/stores-context";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";

beforeEach(() => {
  vi.clearAllMocks();
});


describe("EditAction", () => {
  describe("When tapped the context action should", () => {
    /*
     * On mobile, the user may tap the Edit button in the main search results'
     * context menu which will set the active result and should then make the
     * form visible.
     */
    test("call uiStore's setVisiblePanel", async () => {
      const rootStore = makeMockRootStore({
        uiStore: {
          setVisiblePanel: vi.fn(() => {}),
        },
        searchStore: {
          search: {
            setActiveResult: () => {},
            setEditLoading: () => {},
            editLoading: "no",
          },
          activeResult: {
            setEditing: () => {},
          },
        },
      });
      render(
        <ThemeProvider theme={materialTheme}>
          <storesContext.Provider value={rootStore}>
            <EditAction
              as="button"
              disabled=""
              selectedResults={[makeMockContainer()]}
              closeMenu={() => {}}
            />
          </storesContext.Provider>
        </ThemeProvider>,
      );

      const setVisiblePanelSpy = vi.spyOn(
        rootStore.uiStore,
        "setVisiblePanel",
      );

      await waitFor(() => {
        expect(screen.getByRole("button", { name: "Edit" })).toBeEnabled();
      });

      fireEvent.click(screen.getByRole("button", { name: "Edit" }));

      await waitFor(() => {
        expect(setVisiblePanelSpy).toHaveBeenCalledWith("right");
      });
    });
  });
});


