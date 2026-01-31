/*
 */
import {
  describe,
  expect,
  beforeEach,
  it,
  vi,
} from "vitest";
import "@/__tests__/mocks/useOauthToken";
import "@/__tests__/mocks/useWhoAmI";
import "@/__tests__/mocks/useWebSocketNotifications";
import React from "react";
import {
  render,
  screen,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import App from "../App";
import "../../../__tests__/assertSemanticHeadings";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import materialTheme from "../../../theme";
import { ThemeProvider } from "@mui/material/styles";
import "../../../../__mocks__/matchMedia";
import allIntegrationsAreDisabled from "./allIntegrationsAreDisabled.json";

const mockAxios = new MockAdapter(axios);
const uiNavigationData = {
  userDetails: {
    email: "test@example.com",
    orcidId: null,
    orcidAvailable: false,
    fullName: "Test User",
    username: "test",
    profileImgSrc: null,
  },
  visibleTabs: {
    published: true,
    inventory: true,
    system: true,
    myLabGroups: true,
  },
  extraHelpLinks: [],
  bannerImgSrc: "",
  operatedAs: false,
  nextMaintenance: null,
};

beforeEach(() => {
  vi.clearAllMocks();
  mockAxios.reset();
  mockAxios
    .onGet("/api/v1/userDetails/uiNavigationData")
    .reply(200, uiNavigationData);
  mockAxios.onGet("livechatProperties").reply(200, {
    livechatEnabled: false,
  });
});


describe("Apps page", () => {
  describe("Accessibility", () => {
    it("Should have no axe violations.", async () => {
      mockAxios.onPost("integration/allIntegrations").reply(200, {
        success: false,
        data: null,
        error: "",
      });
      const { container } = render(
        <ThemeProvider theme={materialTheme}>
          <App />
        </ThemeProvider>
      );

      await screen.findAllByText(/Something went wrong!/i);
      await screen.findByAltText("branding");

      // @ts-expect-error toBeAccessible is from @sa11y/vitest
      // eslint-disable-next-line @typescript-eslint/no-unsafe-call
      await expect(container).toBeAccessible();
    });
  });

  it("Has all of the correct headings.", async () => {
    mockAxios.onPost("integration/allIntegrations").reply(200, {
      success: false,
      data: null,
      error: "",
    });

    const { container } = render(
      <ThemeProvider theme={materialTheme}>
        <App />
      </ThemeProvider>
    );

    await screen.findAllByText(/Something went wrong!/i);

    // @ts-expect-error assertHeadings comes from assertSemanticHeadings
    expect(container).assertHeadings([
      { level: 1, content: "Apps" },
      { level: 2, content: "Enabled" },
      { level: 2, content: "Disabled" },
      { level: 2, content: "Unavailable" },
      { level: 2, content: "Third-party RSpace Integrations" },
    ]);
  });
});
