/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, fireEvent } from "@testing-library/react";
import "@testing-library/jest-dom";
import { UsersPage } from "..";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../../../../theme";
import MockAdapter from "axios-mock-adapter";
import * as axios from "axios";
import USER_LISTING from "./userListing";
import PDF_CONFIG from "./pdfConfig";

// Replace JSDOM's Blob with node's so that we have .text()
// $FlowExpectedError[cannot-resolve-module]
window.Blob = require("node:buffer").Blob;

window.RS = { newFileStoresExportEnabled: false };

const mockAxios = new MockAdapter(axios);

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("CSV Export", () => {
  describe("Selection", () => {
    test("When no rows are selected, every row of the current page should be included in the export.", async () => {
      let blob;
      const createObjectURL = jest
        .fn<[Blob], string>()
        .mockImplementation((b) => {
          blob = b;
          return "";
        });
      window.URL.createObjectURL = createObjectURL;
      window.URL.revokeObjectURL = jest.fn();

      mockAxios.onGet("system/ajax/jsonList").reply(200, { ...USER_LISTING });

      mockAxios
        .onGet("/export/ajax/defaultPDFConfig")
        .reply(200, { ...PDF_CONFIG });

      render(
        <StyledEngineProvider injectFirst>
          <ThemeProvider theme={materialTheme}>
            <UsersPage />
          </ThemeProvider>
        </StyledEngineProvider>
      );

      fireEvent.click(await screen.findByRole("button", { name: /Export/ }));
      fireEvent.click(
        screen.getByRole("menuitem", {
          name: /Export this page of rows to CSV/,
        })
      );

      expect(createObjectURL).toHaveBeenCalled();
      if (typeof blob === "undefined")
        throw new Error("Impossible, because createObjectURL has been called");
      expect((await blob.text()).split("\n").length).toBe(11);
    });

    test("When one rows is selected, just it should be included in the csv export.", async () => {
      let blob;
      const createObjectURL = jest
        .fn<[Blob], string>()
        .mockImplementation((b) => {
          blob = b;
          return "";
        });
      window.URL.createObjectURL = createObjectURL;
      window.URL.revokeObjectURL = jest.fn();

      mockAxios.onGet("system/ajax/jsonList").reply(200, { ...USER_LISTING });

      mockAxios
        .onGet("/export/ajax/defaultPDFConfig")
        .reply(200, { ...PDF_CONFIG });

      render(
        <StyledEngineProvider injectFirst>
          <ThemeProvider theme={materialTheme}>
            <UsersPage />
          </ThemeProvider>
        </StyledEngineProvider>
      );

      const checkboxes = await screen.findAllByRole("checkbox", {
        name: /Select row/,
      });
      expect(checkboxes.length).toBe(10);
      fireEvent.click(checkboxes[0]);

      fireEvent.click(screen.getByRole("button", { name: /Export/ }));
      fireEvent.click(
        screen.getByRole("menuitem", {
          name: /Export selected rows to CSV/,
        })
      );

      expect(createObjectURL).toHaveBeenCalled();
      if (typeof blob === "undefined")
        throw new Error("Impossible, because createObjectURL has been called");
      expect((await blob.text()).split("\n").length).toBe(2);
    });
  });
  describe("Column", () => {
    test("All of the columns should be included in the CSV file.", async () => {
      let blob;
      const createObjectURL = jest
        .fn<[Blob], string>()
        .mockImplementation((b) => {
          blob = b;
          return "";
        });
      window.URL.createObjectURL = createObjectURL;
      window.URL.revokeObjectURL = jest.fn();

      mockAxios.onGet("system/ajax/jsonList").reply(200, { ...USER_LISTING });

      mockAxios
        .onGet("/export/ajax/defaultPDFConfig")
        .reply(200, { ...PDF_CONFIG });

      render(
        <StyledEngineProvider injectFirst>
          <ThemeProvider theme={materialTheme}>
            <UsersPage />
          </ThemeProvider>
        </StyledEngineProvider>
      );

      fireEvent.click(
        await screen.findByRole("button", { name: /Select columns/ })
      );
      const numberOfColumns = screen.getAllByRole("checkbox", {
        name: (name) => {
          if (name === "Select all rows") return false;
          if (name === "Select row") return false;
          if (name === "Checkbox selection") return false;
          if (name === "Show/Hide All") return false;
          if (name === "Full Name") return false; // First name and last name are included separately
          return true;
        },
      }).length;

      fireEvent.click(screen.getByRole("button", { name: /Export/ }));
      fireEvent.click(
        screen.getByRole("menuitem", {
          name: /Export this page of rows to CSV/,
        })
      );

      expect(createObjectURL).toHaveBeenCalled();
      if (typeof blob === "undefined")
        throw new Error("Impossible, because createObjectURL has been called");
      expect((await blob.text()).split("\n")[0].split(",").length).toBe(
        numberOfColumns
      );
    });
  });
});
