/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import { render, cleanup, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import MoveDialog from "../MoveDialog";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import { ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "../../../../accentedTheme";
import { ACCENT_COLOR } from "../../../../assets/branding/rspace/gallery";
import "../../../../../__mocks__/matchMedia";
import page1 from "../../__tests__/getUploadedFiles_1.json";
import * as ArrayUtils from "../../../../util/ArrayUtils";

jest.mock("../CallablePdfPreview", () => ({
  usePdfPreview: () => ({
    openPdfPreview: () => {},
  }),
}));

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

const mockAxios = new MockAdapter(axios);
mockAxios.onGet("/collaboraOnline/supportedExts").reply(200, { data: {} });
mockAxios.onGet("/officeOnline/supportedExts").reply(200, { data: {} });

mockAxios.onGet("/userform/ajax/inventoryOauthToken").reply(200, {
  data: "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODAiLCJpYXQiOjE3MzQzNDI5NTYsImV4cCI6MTczNDM0NjU1NiwicmVmcmVzaFRva2VuSGFzaCI6ImZlMTVmYTNkNWUzZDVhNDdlMzNlOWUzNDIyOWIxZWEyMzE0YWQ2ZTZmMTNmYTQyYWRkY2E0ZjE0Mzk1ODJhNGQifQ.HCKre3g_P1wmGrrrnQncvFeT9pAePFSc4UPuyP5oehI",
});
mockAxios.onGet("/gallery/getUploadedFiles").reply(200, page1);
mockAxios.onGet("/collaboraOnline/supportedExts").reply(200, { data: {} });
mockAxios.onGet("/officeOnline/supportedExts").reply(200, { data: {} });

describe("MoveDialog", () => {
  test("Should request only folders", async () => {
    render(
      <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
        <MoveDialog
          open={true}
          onClose={() => {}}
          section="Images"
          refreshListing={() => Promise.resolve()}
        />
      </ThemeProvider>
    );

    await waitFor(() => {
      const getUploadedFilesCalls = mockAxios.history.get.filter(
        ({ url }: { url?: string }) => /getUploadedFiles/.test(url ?? "")
      );
      expect(getUploadedFilesCalls.length).toBe(1);
    });

    const getUploadedFilesCalls = mockAxios.history.get.filter(
      ({ url }: { url?: string }) => /getUploadedFiles/.test(url ?? "")
    );
    expect(
      ArrayUtils.head(getUploadedFilesCalls)
        .map(({ params }: { params?: URLSearchParams }) =>
          params?.get("foldersOnly")
        )
        .orElse("false")
    ).toEqual("true");
  });
});
