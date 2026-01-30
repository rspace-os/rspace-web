/*
 */
import {
  describe,
  test,
  expect,
  vi,
  beforeEach,
} from "vitest";
import "../../../../../__mocks__/matchMedia";
import React from "react";
import {
  render,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import Sidebar from "../Sidebar";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import { makeMockRootStore } from "../../../../stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "../../../../stores/stores-context";
import { axe } from "vitest-axe";
import { toHaveNoViolations } from "vitest-axe/matchers";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import { LandmarksProvider } from "../../../../components/LandmarksContext";

expect.extend({ toHaveNoViolations });

const mockAxios = new MockAdapter(axios);

beforeEach(() => {
  vi.clearAllMocks();
});


describe("Sidebar", () => {
  test("Should have no axe violations.", async () => {
    mockAxios.onGet("livechatProperties").reply(200, {
      livechatEnabled: false,
    });
    const rootStore = makeMockRootStore({
      uiStore: {
        alwaysVisibleSidebar: true,
        sidebarOpen: true,
      },
      searchStore: {
        search: {
          benchSearch: true,
        },
      },
    });
    const { container } = render(
      <ThemeProvider theme={materialTheme}>
        <LandmarksProvider>
          <storesContext.Provider value={rootStore}>
            <Sidebar id="foo" />
          </storesContext.Provider>
        </LandmarksProvider>
      </ThemeProvider>
    );

    expect(await axe(container)).toHaveNoViolations();
  });
});


