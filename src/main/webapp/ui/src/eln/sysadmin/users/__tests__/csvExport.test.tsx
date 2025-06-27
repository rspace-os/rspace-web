/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, fireEvent } from "@testing-library/react";
import "@testing-library/jest-dom";
import { UsersPage } from "..";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../../../../theme";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import USER_LISTING from "./userListing.json";
import PDF_CONFIG from "./pdfConfig.json";
import userEvent from "@testing-library/user-event";

// Replace JSDOM's Blob with node's so that we have .text()
// eslint-disable-next-line @typescript-eslint/no-unsafe-assignment, @typescript-eslint/no-require-imports, @typescript-eslint/no-unsafe-member-access
window.Blob = require("node:buffer").Blob;

// @ts-expect-error global
window.RS = { newFileStoresExportEnabled: false };

const mockAxios = new MockAdapter(axios);

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("CSV Export", () => {
  describe("Selection", () => {
    test("When no rows are selected, every row of the current page should be included in the export.", async () => {
      let blob: Blob | undefined;
      const createObjectURL = jest.fn().mockImplementation((b: Blob) => {
        blob = b;
        return "";
      });
      window.URL.createObjectURL = createObjectURL;
      window.URL.revokeObjectURL = jest.fn();

      mockAxios.onGet("system/ajax/jsonList").reply(200, { ...USER_LISTING });

      mockAxios
        .onGet("/export/ajax/defaultPDFConfig")
        .reply(200, { ...PDF_CONFIG });

      mockAxios.onGet("/analyticsProperties").reply(200, {
        analyticsEnabled: false,
      });

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
      let blob: Blob | undefined;
      const createObjectURL = jest.fn().mockImplementation((b: Blob) => {
        blob = b;
        return "";
      });
      window.URL.createObjectURL = createObjectURL;
      window.URL.revokeObjectURL = jest.fn();

      mockAxios.onGet("system/ajax/jsonList").reply(200, { ...USER_LISTING });

      mockAxios
        .onGet("/export/ajax/defaultPDFConfig")
        .reply(200, { ...PDF_CONFIG });

      mockAxios.onGet("/analyticsProperties").reply(200, {
        analyticsEnabled: false,
      });

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
      let blob: Blob | undefined;
      const createObjectURL = jest.fn().mockImplementation((b: Blob) => {
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
    test("The usage column should be a precise number.", async () => {
      const user = userEvent.setup();
      let blob: Blob | undefined;
      const createObjectURL = jest.fn().mockImplementation((b: Blob) => {
        blob = b;
        return "";
      });
      window.URL.createObjectURL = createObjectURL;
      window.URL.revokeObjectURL = jest.fn();

      mockAxios.onGet("system/ajax/jsonList").reply(200, { ...USER_LISTING });

      mockAxios
        .onGet("/userform/ajax/preference?preference=UI_JSON_SETTINGS")
        .reply(200, {});
      mockAxios.onPost("/userform/ajax/preference").reply(200, {});
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

      await user.click(await screen.findByRole("button", { name: /Export/ }));
      await user.click(
        screen.getByRole("menuitem", {
          name: /Export this page of rows to CSV/,
        })
      );

      expect(createObjectURL).toHaveBeenCalled();
      if (typeof blob === "undefined")
        throw new Error("Impossible, because createObjectURL has been called");
      const csvData = await blob.text();
      expect(csvData).toContain("362006");
      expect(csvData).not.toContain("362.01 kB");
    });
  });
});
