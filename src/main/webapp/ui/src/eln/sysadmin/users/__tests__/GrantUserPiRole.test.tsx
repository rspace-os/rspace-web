/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import {
  render,
  cleanup,
  screen,
  within,
  act,
  waitFor,
} from "@testing-library/react";
import "@testing-library/jest-dom";
import { UsersPage } from "..";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import USER_LISTING from "./userListing.json";
import PDF_CONFIG from "./pdfConfig.json";
import userEvent from "@testing-library/user-event";

const mockAxios = new MockAdapter(axios);

// @ts-expect-error global
window.RS = { newFileStoresExportEnabled: false };

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("Grant User PI Role", () => {
  test(
    "When `checkVerificationPasswordNeeded` returns true, a message should be shown.",
    async () => {
      const user = userEvent.setup();
      const createObjectURL = jest.fn().mockImplementation(() => "");
      window.URL.createObjectURL = createObjectURL;
      window.URL.revokeObjectURL = jest.fn();

      mockAxios.onGet("system/ajax/jsonList").reply(200, { ...USER_LISTING });

      mockAxios
        .onGet("/export/ajax/defaultPDFConfig")
        .reply(200, { ...PDF_CONFIG });

      mockAxios
        .onGet("/vfpwd/ajax/checkVerificationPasswordNeeded")
        .reply(200, { data: true });

      render(
        <ThemeProvider theme={materialTheme}>
          <UsersPage />
        </ThemeProvider>
      );

      await waitFor(() => {
        expect(screen.getByRole("grid")).toBeVisible();
      });

      await waitFor(() => {
        expect(
          within(screen.getByRole("grid")).getAllByRole("row").length
        ).toBeGreaterThan(1);
      });

      await waitFor(() => {
        expect(screen.getByRole("row", { name: /user8h/ })).toBeVisible();
      });

      const checkbox = within(
        await screen.findByRole("row", { name: /user8h/ })
      ).getByRole("checkbox");

      await act(async () => {
        await user.click(checkbox);
      });

      await act(async () => {
        await user.click(screen.getByRole("button", { name: /Actions/ }));
      });
      await act(async () => {
        await user.click(
          screen.getByRole("menuitem", { name: /Grant PI role/ })
        );
      });

      expect(await screen.findByRole("dialog")).toBeVisible();

      expect(
        within(screen.getByRole("dialog")).getByText(
          "Please set your verification password in My RSpace before performing this action."
        )
      ).toBeVisible();
    },
    40 * 1000
  );
  test(
    "When `checkVerificationPasswordNeeded` returns false, a message should not be shown.",
    async () => {
      const user = userEvent.setup();
      const createObjectURL = jest.fn().mockImplementation(() => "");
      window.URL.createObjectURL = createObjectURL;
      window.URL.revokeObjectURL = jest.fn();

      mockAxios.onGet("system/ajax/jsonList").reply(200, { ...USER_LISTING });

      mockAxios
        .onGet("/export/ajax/defaultPDFConfig")
        .reply(200, { ...PDF_CONFIG });

      mockAxios
        .onGet("/vfpwd/ajax/checkVerificationPasswordNeeded")
        .reply(200, { data: false });

      render(
        <ThemeProvider theme={materialTheme}>
          <UsersPage />
        </ThemeProvider>
      );

      await waitFor(() => {
        expect(screen.getByRole("grid")).toBeVisible();
      });

      await waitFor(() => {
        expect(
          within(screen.getByRole("grid")).getAllByRole("row").length
        ).toBeGreaterThan(1);
      });

      const checkbox = within(
        await screen.findByRole("row", { name: /user8h/ })
      ).getByRole("checkbox");

      await act(async () => {
        await user.click(checkbox);
      });

      await act(async () => {
        await user.click(screen.getByRole("button", { name: /Actions/ }));
      });
      await act(async () => {
        await user.click(
          screen.getByRole("menuitem", { name: /Grant PI role/ })
        );
      });

      expect(await screen.findByRole("dialog")).toBeVisible();

      await waitFor(() => {
        expect(
          screen.getByText((content) => {
            return (
              content ===
              "To grant the PI role to please re-enter your password."
            );
          })
        ).toBeVisible();
      });
    },
    40 * 1000
  );
});
