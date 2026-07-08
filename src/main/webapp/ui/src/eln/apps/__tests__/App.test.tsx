import { beforeEach, describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/useOauthToken";
import "@/__tests__/__mocks__/useWhoAmI";
import "@/__tests__/__mocks__/useWebSocketNotifications";
import { render, screen } from "@testing-library/react";
import App from "../App";
import "@/__tests__/assertSemanticHeadings";
import { ThemeProvider } from "@mui/material/styles";
import MockAdapter from "axios-mock-adapter";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import axios from "@/common/axios";
import ErrorBoundary from "@/components/ErrorBoundary";
import materialTheme from "../../../theme";
import allIntegrationsAreDisabled from "./allIntegrationsAreDisabled.json";

import "@/__tests__/__mocks__/matchMedia";

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
  mockAxios.onGet("/api/v1/userDetails/uiNavigationData").reply(200, uiNavigationData);
  mockAxios.onGet("livechatProperties").reply(200, {
    livechatEnabled: false,
  });
});

function renderApp() {
  return render(
    <ThemeProvider theme={materialTheme}>
      <ErrorBoundary>
        <App />
      </ErrorBoundary>
    </ThemeProvider>,
  );
}

describe("Apps page", () => {
  describe("Accessibility", () => {
    test("Should have no axe violations.", async () => {
      const restoreConsole = silenceConsole(["error"], [/.*/]);
      try {
        mockAxios.onGet("integration/allIntegrations").reply(200, {
          success: false,
          data: null,
          error: "",
        });
        const { container } = renderApp();
        await screen.findByText("common:errorBoundary.message");

        // @ts-expect-error toBeAccessible is from @sa11y/vitest
        // eslint-disable-next-line @typescript-eslint/no-unsafe-call
        await expect(container).toBeAccessible();
      } finally {
        restoreConsole();
      }
    });
  });
  test("Has all of the correct headings.", async () => {
    mockAxios.onGet("integration/allIntegrations").reply(200, allIntegrationsAreDisabled);
    const { container } = renderApp();

    await screen.findByText("apps:integrations.dataverse.name");
    // @ts-expect-error assertHeadings comes from assertSemanticHeadings
    // eslint-disable-next-line @typescript-eslint/no-unsafe-call
    expect(container).assertHeadings([
      { level: 1, content: "apps:page.title" },
      { level: 2, content: "apps:page.sections.enabled.title" },
      { level: 2, content: "apps:page.sections.disabled.title" },
      { level: 2, content: "apps:page.sections.unavailable.title" },
      { level: 2, content: "apps:page.sections.external.title" },
    ]);
  });
});
