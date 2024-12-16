/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "../../../../../__mocks__/matchMedia";
import React from "react";
import { render, cleanup, screen, act } from "@testing-library/react";
import "@testing-library/jest-dom";
import userEvent from "@testing-library/user-event";
import Sidebar from "../Sidebar";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import MockAdapter from "axios-mock-adapter";
import * as axios from "axios";
import { DUMMY_ID } from "../../useGalleryListing";

function newMockAxios() {
  const mockAxios = new MockAdapter(axios);

  mockAxios.onGet("/integration/integrationInfo?name=DMPTOOL").reply(200, {
    data: {
      name: "DMPTOOL",
      displayName: "DMPtool",
      available: true,
      enabled: false,
      oauthConnected: false,
      options: {},
    },
    error: null,
    success: true,
    errorMsg: null,
  });

  mockAxios.onGet("/integration/integrationInfo?name=DMPONLINE").reply(200, {
    data: {
      name: "DMPONLINE",
      displayName: "DMPonline",
      available: true,
      enabled: true,
      oauthConnected: false,
      options: {},
    },
    error: null,
    success: true,
    errorMsg: null,
  });

  mockAxios.onGet("/integration/integrationInfo?name=ARGOS").reply(200, {
    data: {
      name: "ARGOS",
      displayName: "Argos",
      available: false,
      enabled: false,
      oauthConnected: false,
      options: {},
    },
    error: null,
    success: true,
    errorMsg: null,
  });

  mockAxios.onPost("gallery/ajax/createFolder").reply(200, {
    data: true,
    error: null,
    success: true,
    errorMsg: null,
  });

  return mockAxios;
}

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("Sidebar", () => {
  describe("New Folder", () => {
    test("Clicking the Submit button should work", async () => {
      const user = userEvent.setup();
      const mockAxios = newMockAxios();

      const { baseElement } = render(
        <ThemeProvider theme={materialTheme}>
          <Sidebar
            selectedSection="Images"
            setSelectedSection={() => {}}
            drawerOpen
            setDrawerOpen={() => {}}
            path={[]}
            folderId={{ tag: "success", value: DUMMY_ID }}
            refreshListing={() => {}}
            id="1"
          />
        </ThemeProvider>
      );

      await act(async () => {
        await user.click(screen.getByRole("button", { name: "Create" }));
      });

      const menuitem = await screen.findByRole("menuitem", {
        name: /New Folder/i,
      });
      await act(async () => {
        await user.click(menuitem);
      });

      await act(async () => {
        await user.type(await screen.findByRole("textbox"), "test");
      });

      await act(async () => {
        await user.click(
          await screen.findByRole("button", { name: /create/i })
        );
      });

      expect(mockAxios.history.post.length).toBeGreaterThan(0);
    });
    test("Pressing enter to Submit should work", async () => {
      const user = userEvent.setup();
      const mockAxios = newMockAxios();

      const { baseElement } = render(
        <ThemeProvider theme={materialTheme}>
          <Sidebar
            selectedSection="Images"
            setSelectedSection={() => {}}
            drawerOpen
            setDrawerOpen={() => {}}
            path={[]}
            folderId={{ tag: "success", value: DUMMY_ID }}
            refreshListing={() => {}}
            id="1"
          />
        </ThemeProvider>
      );

      await act(async () => {
        await user.click(screen.getByRole("button", { name: "Create" }));
      });

      const menuitem = await screen.findByRole("menuitem", {
        name: /New Folder/i,
      });
      await act(async () => {
        await user.click(menuitem);
      });

      await act(async () => {
        await user.type(await screen.findByRole("textbox"), "test{enter}");
      });

      expect(mockAxios.history.post.length).toBeGreaterThan(0);
    });
  });
});
