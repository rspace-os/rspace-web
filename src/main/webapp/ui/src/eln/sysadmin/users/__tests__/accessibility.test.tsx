import { describe, expect, test, vi } from "vitest";
// eslint-disable-next-line vitest/no-mocks-import
import "@/__tests__/__mocks__/useUiPreference";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { screen, waitFor } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";
import { expectAccessible, render, within } from "@/__tests__/customQueries";
import axios from "@/common/axios";
import { UsersPage } from "@/eln/sysadmin/users";
import materialTheme from "../../../../theme";
import PDF_CONFIG from "./pdfConfig.json";
import USER_LISTING from "./userListing.json";

vi.mock("@/modules/common/hooks/auth", () => ({
  useOauthTokenQuery: () => ({ data: "test-token" }),
}));

vi.mock("@/modules/raid/queries", () => ({
  useRaidIntegrationInfoAjaxQuery: () => ({
    data: { success: true, data: { enabled: false } },
  }),
}));

vi.mock("@/modules/share/queries", () => ({
  useCommonGroupsShareListingQuery: () => ({ data: new Map() }),
}));

// `RS.newFileStoresExportEnabled` is read by the production UsersPage
// (eln/sysadmin/users/index.tsx) via the loosely-typed global `RS`, but the
// strict `RSGlobal` interface (used for `window.RS`) does not declare it. Augment
// it here so the test stub assignment type-checks. This global augmentation also
// covers the sibling sysadmin tests that set the same property.
declare global {
  interface RSGlobal {
    newFileStoresExportEnabled?: boolean;
  }
}

window.RS = { newFileStoresExportEnabled: false };

const mockAxios = new MockAdapter(axios);
describe("Accessibility", () => {
  test("Should have no axe violations.", async () => {
    mockAxios.onGet("system/ajax/jsonList").reply(200, { ...USER_LISTING });
    mockAxios.onGet("/userform/ajax/preference?preference=UI_JSON_SETTINGS").reply(200, {});
    mockAxios.onPost("/userform/ajax/preference").reply(200, {});
    mockAxios.onGet("/export/ajax/defaultPDFConfig").reply(200, { ...PDF_CONFIG });
    mockAxios.onGet("/analyticsProperties").reply(200, {
      analyticsEnabled: false,
    });
    const { baseElement } = render(
      <StyledEngineProvider injectFirst>
        <ThemeProvider theme={materialTheme}>
          <UsersPage />
        </ThemeProvider>
      </StyledEngineProvider>,
    );
    // Wait for the table to be loaded with rows
    const grid = await screen.findByRole("grid");
    await waitFor(() => expect(within(grid).getAllByRole("row").length).toBeGreaterThan(1));
    await expectAccessible(baseElement);
  });
});
