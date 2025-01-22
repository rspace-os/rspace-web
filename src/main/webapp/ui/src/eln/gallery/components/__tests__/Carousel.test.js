/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import userEvent from "@testing-library/user-event";
import Carousel from "../Carousel";
import * as FetchingData from "../../../../util/fetchingData";
import { useGalleryListing } from "../../useGalleryListing";
import MockAdapter from "axios-mock-adapter";
import * as axios from "axios";
import page1 from "../../__tests__/getUploadedFiles_1.json";

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

const mockAxios = new MockAdapter(axios);

mockAxios.onGet("/userform/ajax/inventoryOauthToken").reply(200, {
  data: "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODAiLCJpYXQiOjE3MzQzNDI5NTYsImV4cCI6MTczNDM0NjU1NiwicmVmcmVzaFRva2VuSGFzaCI6ImZlMTVmYTNkNWUzZDVhNDdlMzNlOWUzNDIyOWIxZWEyMzE0YWQ2ZTZmMTNmYTQyYWRkY2E0ZjE0Mzk1ODJhNGQifQ.HCKre3g_P1wmGrrrnQncvFeT9pAePFSc4UPuyP5oehI",
});

describe("Carousel", () => {
  test("Should show an indicator of progress through listing.", async () => {
    function Wrapper() {
      const initialLocation = React.useMemo(
        () => ({ tag: "section", section: "Images" }),
        []
      );
      const { galleryListing } = useGalleryListing({
        initialLocation,
        searchTerm: "",
        sortOrder: "DESC",
        orderBy: "modificationDate",
      });

      return FetchingData.getSuccessValue(galleryListing)
        .map((listing) => {
          if (listing.tag === "empty") return null;
          return <Carousel listing={listing} key={null} />;
        })
        .orElse(null);
    }
    const user = userEvent.setup();

    mockAxios.onGet("/collaboraOnline/supportedExts").reply(200, { data: {} });
    mockAxios.onGet("/officeOnline/supportedExts").reply(200, { data: {} });
    mockAxios.onGet("/gallery/getUploadedFiles").reply(200, page1);
    render(<Wrapper />);

    await waitFor(() => {
      expect(screen.getByText("1 / 34")).toBeVisible();
    });

    await user.click(screen.getByRole("button", { name: /next/i }));

    await waitFor(() => {
      expect(screen.getByText("2 / 34")).toBeVisible();
    });
  });

  test("Moving to a different file resets the zoom level", async () => {
    function Wrapper() {
      const initialLocation = React.useMemo(
        () => ({ tag: "section", section: "Images" }),
        []
      );
      const { galleryListing } = useGalleryListing({
        initialLocation,
        searchTerm: "",
        sortOrder: "DESC",
        orderBy: "modificationDate",
      });

      return FetchingData.getSuccessValue(galleryListing)
        .map((listing) => {
          if (listing.tag === "empty") return null;
          return <Carousel listing={listing} key={null} />;
        })
        .orElse(null);
    }
    const user = userEvent.setup();

    mockAxios.onGet("/collaboraOnline/supportedExts").reply(200, { data: {} });
    mockAxios.onGet("/officeOnline/supportedExts").reply(200, { data: {} });
    mockAxios.onGet("/gallery/getUploadedFiles").reply(200, page1);
    render(<Wrapper />);

    const resetZoomButton = await screen.findByRole("button", {
      name: /reset zoom/i,
    });

    expect(resetZoomButton).toBeDisabled();
    await user.click(screen.getByRole("button", { name: /zoom in/i }));
    expect(resetZoomButton).toBeEnabled();
    await user.click(screen.getByRole("button", { name: /next/i }));
    expect(resetZoomButton).toBeDisabled();
  });
});
