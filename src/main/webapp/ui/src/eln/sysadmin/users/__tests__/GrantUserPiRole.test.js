/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import {
  render,
  cleanup,
  screen,
  fireEvent,
  within,
  act,
  waitFor,
} from "@testing-library/react";
import "@testing-library/jest-dom";
import { UsersPage } from "..";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../../../../theme";
import MockAdapter from "axios-mock-adapter";
import * as axios from "axios";
import USER_LISTING from "./userListing";
import PDF_CONFIG from "./pdfConfig";
import { sleep } from "../../../../util/Util";

const mockAxios = new MockAdapter(axios);

window.RS = { newFileStoresExportEnabled: false };

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("Grant User PI Role", () => {
  test(
    "When `checkVerificationPasswordNeeded` returns true, a message should be shown.",
    async () => {
      const createObjectURL = jest
        .fn<[Blob], string>()
        .mockImplementation(() => "");
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
        <StyledEngineProvider injectFirst>
          <ThemeProvider theme={materialTheme}>
            <UsersPage />
          </ThemeProvider>
        </StyledEngineProvider>
      );

      await act(() => {
        return sleep(1000);
      });

      const checkbox = within(
        await screen.findByRole("row", { name: /user8h/ })
      ).getByRole("checkbox");

      fireEvent.click(checkbox);

      fireEvent.click(screen.getByRole("button", { name: /Actions/ }));
      fireEvent.click(screen.getByRole("menuitem", { name: /Grant PI role/ }));

      expect(await screen.findByRole("dialog")).toBeVisible();

      await act(() => {
        return sleep(100);
      });

      expect(
        within(screen.getByRole("dialog")).getByText(
          "Please set your verification password in My RSpace before performing this action."
        )
      ).toBeVisible();
    },
    20 * 1000
  );
  test(
    "When `checkVerificationPasswordNeeded` returns false, a message should not be shown.",
    async () => {
      const createObjectURL = jest
        .fn<[Blob], string>()
        .mockImplementation(() => "");
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
        <StyledEngineProvider injectFirst>
          <ThemeProvider theme={materialTheme}>
            <UsersPage />
          </ThemeProvider>
        </StyledEngineProvider>
      );

      await act(() => {
        return sleep(100);
      });

      const checkbox = within(
        await screen.findByRole("row", { name: /user8h/ })
      ).getByRole("checkbox");

      fireEvent.click(checkbox);

      fireEvent.click(screen.getByRole("button", { name: /Actions/ }));
      fireEvent.click(screen.getByRole("menuitem", { name: /Grant PI role/ }));

      await waitFor(() => {
        expect(screen.getByRole("dialog")).toBeVisible();
      });

      await act(() => {
        return sleep(100);
      });

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
    20 * 1000
  );
});
