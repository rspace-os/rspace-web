/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import DMPToolMenuItem from "../DMPToolMenuItem";
import MockAdapter from "axios-mock-adapter";
import * as axios from "axios";
import materialTheme from "../../../theme";
import { ThemeProvider } from "@mui/material/styles";

const mockAxios = new MockAdapter(axios);

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("DMPToolMenuItem", () => {
  test("If the DMPTool is enabled but the user is not authenticated then the menu item should be disabled.", async () => {
    mockAxios.onGet("/apps/dmptool/baseUrlHost").reply(200, "example.com");
    mockAxios.onGet("/integration/integrationInfo").reply(200, {
      data: {
        available: true,
        displayName: "DMPTool",
        enabled: true,
        name: "DMPTOOL",
        oauthConnected: false,
        options: {},
      },
    });

    render(
      <ThemeProvider theme={materialTheme}>
        <DMPToolMenuItem onClick={() => {}} />
      </ThemeProvider>
    );

    void (await waitFor(async () => {
      expect(await screen.findByRole("menuitem")).toBeVisible();
    }));

    expect(screen.getByRole("menuitem")).toHaveAttribute(
      "aria-disabled",
      "true"
    );
    expect(
      screen.getByText("You have not yet authenticated on the apps page.")
    ).toBeVisible();
  });
});
