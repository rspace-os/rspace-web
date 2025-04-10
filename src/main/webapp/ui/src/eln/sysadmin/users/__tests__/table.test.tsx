/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import { cleanup, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import { UsersPage } from "..";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../../../../theme";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import USER_LISTING from "./userListing.json";
import PDF_CONFIG from "./pdfConfig.json";
import { render, within } from "../../../../__tests__/customQueries";

// @ts-expect-error global
window.RS = { newFileStoresExportEnabled: false };

const mockAxios = new MockAdapter(axios);

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("Table Listing", () => {
  test("Usage should be shown in human-readable format", async () => {
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

    const grid = await screen.findByRole("grid");
    await waitFor(() =>
      expect(within(grid).getAllByRole("row").length).toBeGreaterThan(1)
    );

    expect(
      // @ts-expect-error findTableCell exists on the custom within function
      // eslint-disable-next-line @typescript-eslint/no-unsafe-call
      await within(grid).findTableCell({ columnHeading: "Usage", rowIndex: 1 })
    ).toHaveTextContent("362.01 kB");
    expect(
      // @ts-expect-error findTableCell exists on the custom within function
      // eslint-disable-next-line @typescript-eslint/no-unsafe-call
      await within(grid).findTableCell({ columnHeading: "Usage", rowIndex: 2 })
    ).toHaveTextContent("0 B");
  });
});
