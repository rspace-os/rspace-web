/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "../../../../../__mocks__/matchMedia";
import React from "react";
import { render, cleanup } from "@testing-library/react";
import "@testing-library/jest-dom";
import Sidebar from "../Sidebar";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import { makeMockRootStore } from "../../../../stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "../../../../stores/stores-context";
import { axe, toHaveNoViolations } from "jest-axe";
import MockAdapter from "axios-mock-adapter";
import * as axios from "axios";

expect.extend(toHaveNoViolations);

const mockAxios = new MockAdapter(axios);

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

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
        <storesContext.Provider value={rootStore}>
          <Sidebar id="foo" />
        </storesContext.Provider>
      </ThemeProvider>
    );

    // $FlowExpectedError[incompatible-call] See expect.extend above
    expect(await axe(container)).toHaveNoViolations();
  });
});
