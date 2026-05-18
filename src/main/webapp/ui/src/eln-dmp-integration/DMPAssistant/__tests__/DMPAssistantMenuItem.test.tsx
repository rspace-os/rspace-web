import { test, describe, expect } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import DMPAssistantMenuItem from "../DMPAssistantMenuItem";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import materialTheme from "../../../theme";
import { ThemeProvider } from "@mui/material/styles";

const mockAxios = new MockAdapter(axios);

describe("DMPAssistantMenuItem", () => {
  test("Renders the menu item when the integration is enabled.", async () => {
    mockAxios.onGet("/integration/integrationInfo").reply(200, {
      data: {
        available: true,
        displayName: "DMP Assistant",
        enabled: true,
        name: "DMPASSISTANT",
        oauthConnected: true,
        options: {},
      },
    });
    render(
      <ThemeProvider theme={materialTheme}>
        <DMPAssistantMenuItem onClick={() => {}} />
      </ThemeProvider>
    );

    void (await waitFor(async () => {
      expect(
        await screen.findByText("DMP from DMP Assistant")
      ).toBeVisible();
    }));
  });

  test("Hides the menu item when the integration is disabled.", async () => {
    mockAxios.onGet("/integration/integrationInfo").reply(200, {
      data: {
        available: true,
        displayName: "DMP Assistant",
        enabled: false,
        name: "DMPASSISTANT",
        oauthConnected: false,
        options: {},
      },
    });
    const { container } = render(
      <ThemeProvider theme={materialTheme}>
        <DMPAssistantMenuItem onClick={() => {}} />
      </ThemeProvider>
    );

    await waitFor(() => {
      expect(container).toBeEmptyDOMElement();
    });
  });
});
