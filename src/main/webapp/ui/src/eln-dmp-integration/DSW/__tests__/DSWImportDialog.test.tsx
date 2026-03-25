import { test, describe, expect, beforeEach, afterEach, vi } from 'vitest';
import "@/__tests__/mocks/useOauthToken";
import "@/__tests__/mocks/useWhoAmI";
import "@/__tests__/mocks/useWebSocketNotifications";
import "../../../../__mocks__/matchMedia";
import React from "react";
import {
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import DSWImportDialog from "../DSWImportDialog";
import materialTheme from "../../../theme";
import { ThemeProvider } from "@mui/material/styles";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";

import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import {DswConfig} from "@/eln-dmp-integration/DSW/DSWAccentMenuItem";
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

const connectionSettings : DswConfig = {
  DSW_ALIAS: "dswAlias",
  DSW_APIKEY: "XXXXXXXXXXXXXXXXX",
  DSW_URL: "dsw.org"
}

let restoreConsole = () => {};
beforeEach(() => {
  vi.clearAllMocks();
  mockAxios.reset();
  mockAxios
    .onGet("/api/v1/userDetails/uiNavigationData")
    .reply(200, uiNavigationData);
  mockAxios
    .onGet("/apps/dmptool/baseUrlHost")
    .reply(200, "https://dmptool.org");
  restoreConsole = silenceConsole(
    ["info"],
    ["The response from this request is being discarded"]
  );
});
afterEach(() => {
  restoreConsole();

});
describe("DSWImportDialog", () => {

  test("No DMPs message is shown when no DMPs are returned.", async () => {
    mockAxios
      .onGet(`/apps/dsw/plans?serverAlias=${connectionSettings.DSW_ALIAS}`)
      .reply(200, {
        success: true,
        data: [],
        error: {}
      });

    render(
      <ThemeProvider theme={materialTheme}>
        <DSWImportDialog open setOpen={() => {}} connection={connectionSettings}/>
      </ThemeProvider>

    );
    await waitFor(() => {
      expect(screen.getByText("No projects found")).toBeVisible();
    });
  });

  test("The correct columns are displayed by default.", async () => {
    mockAxios.onGet(`/apps/dsw/plans?serverAlias=${connectionSettings.DSW_ALIAS}`)
      .reply(200, {
        success: true,
        data: [
            {
              createdAt: "2026-02-17T14:50:44.563928Z",
              description: "Description for MockProject01",
              name: "MockProject01",
              sharing: "RestrictedProjectSharing",
              state: "DefaultProjectState",
              template: false,
              updatedAt: "2026-02-17T14:51:14.358743Z",
              uuid: "abcd-1234",
              visibility: "PrivateProjectVisibility",
            },
            {
              createdAt: "2026-02-18T14:50:44.563928Z",
              description: "Description for MockProject02",
              name: "MockProject02",
              sharing: "RestrictedProjectSharing",
              state: "DefaultProjectState",
              template: false,
              updatedAt: "2026-02-18T14:51:14.358743Z",
              uuid: "ncc-1701",
              visibility: "PrivateProjectVisibility",
            }
        ],
        error: {}
      });

    render(
      <ThemeProvider theme={materialTheme}>
        <DSWImportDialog open setOpen={() => {}} connection={connectionSettings}/>
      </ThemeProvider>

    );

    await waitFor(() => {
      expect(screen.getByText("Select")).toBeVisible();
      expect(screen.getByText("Name")).toBeVisible();
      expect(screen.getByText("Description")).toBeVisible();
      expect(screen.getByText("Updated At")).toBeVisible();
      expect(screen.getAllByRole("row")).toHaveLength(3); // Includes table header

      expect(screen.getByText("MockProject01")).toBeVisible();
      expect(screen.getByText("MockProject02")).toBeVisible();
    });
  });

});
