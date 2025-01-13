/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "../../../../../__mocks__/matchMedia";
import React from "react";
import { render, cleanup } from "@testing-library/react";
import "@testing-library/jest-dom";
import { axe, toHaveNoViolations } from "jest-axe";
import MainPanel from "../MainPanel";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import { BrowserRouter } from "react-router-dom";
import MockAdapter from "axios-mock-adapter";
import * as axios from "axios";
import page1 from "../../__tests__/getUploadedFiles_1";

expect.extend(toHaveNoViolations);

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

jest.mock("react-pdf", () => ({
  Document: () => null,
  Page: () => {},
  pdfjs: {
    GlobalWorkerOptions: {
      workerSrc: null,
    },
  },
}));

jest.mock("../MoveToIrods", () => ({
  __esModule: true,
  default: () => null,
  COLOR: {
    main: { hue: 0, lightness: 0, saturation: 0 },
    background: { hue: 0, lightness: 0, saturation: 0 },
    contrastText: { hue: 0, lightness: 0, saturation: 0 },
  },
}));

const mockAxios = new MockAdapter(axios);
mockAxios.onGet("/collaboraOnline/supportedExts").reply(200, { data: {} });
mockAxios.onGet("/officeOnline/supportedExts").reply(200, { data: {} });
mockAxios
  .onGet("/export/ajax/defaultPDFConfig")
  .reply(200, { data: { pageSize: "A4" } });
mockAxios.onGet("/gallery/getUploadedFiles").reply(200, page1);

describe("MainPanel", () => {
  test("Has no accessibility violations", async () => {
    const { container } = render(
      <BrowserRouter>
        <ThemeProvider theme={materialTheme}>
          <MainPanel
            selectedSection="Images"
            path={[]}
            clearPath={() => {}}
            galleryListing={{ tag: "loading" }}
            folderId={{ tag: "loading" }}
            refreshListing={() => Promise.resolve()}
            sortOrder="DESC"
            orderBy="modificationDate"
            setSortOrder={() => {}}
            setOrderBy={() => {}}
          />
        </ThemeProvider>
      </BrowserRouter>
    );

    // $FlowExpectedError[incompatible-call] See expect.extend above
    expect(await axe(container)).toHaveNoViolations();
  });
});
