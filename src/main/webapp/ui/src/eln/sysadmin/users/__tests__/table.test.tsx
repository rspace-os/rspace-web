import { describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/useUiPreference";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { render, screen, waitFor, within } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";

import { findTableCell } from "@/__tests__/tableQueries";
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

window.RS = { newFileStoresExportEnabled: false };

const mockAxios = new MockAdapter(axios);
describe("Table Listing", () => {
  test("Usage should be shown in human-readable format", async () => {
    mockAxios.onGet("system/ajax/jsonList").reply(200, { ...USER_LISTING });
    mockAxios.onGet("/userform/ajax/preference?preference=UI_JSON_SETTINGS").reply(200, {});
    mockAxios.onPost("/userform/ajax/preference").reply(200, {});
    mockAxios.onGet("/export/ajax/defaultPDFConfig").reply(200, { ...PDF_CONFIG });
    mockAxios.onGet("/analyticsProperties").reply(200, {
      analyticsEnabled: false,
    });
    render(
      <StyledEngineProvider injectFirst>
        <ThemeProvider theme={materialTheme}>
          <UsersPage />
        </ThemeProvider>
      </StyledEngineProvider>,
    );
    const grid = await screen.findByRole("grid");
    await waitFor(() => expect(within(grid).getAllByRole("row").length).toBeGreaterThan(1));
    expect(
      await findTableCell(grid, { columnHeading: "system:usersPage.columns.usage", rowIndex: 1 }),
      // 362006 bytes: pretty-bytes rounds 362.006 kB to "362 kB" (the previous
      // hand-rolled toFixed(2) produced "362.01 kB")
    ).toHaveTextContent("362 kB");
    expect(
      await findTableCell(grid, { columnHeading: "system:usersPage.columns.usage", rowIndex: 2 }),
    ).toHaveTextContent("0 B");
  });
});
