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
import {
  useGallerySelection,
  GallerySelection,
} from "../../useGallerySelection";
import { observer } from "mobx-react-lite";

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

describe("Carousel", () => {
  test("Should show an indicator of progress through listing.", async () => {
    function Wrapper() {
      const { galleryListing } = useGalleryListing({
        section: "Images",
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

  test("When a file is already selected, carousel should default to that file.", async () => {
    const Wrapper = observer(() => {
      const selection = useGallerySelection();
      const { galleryListing } = useGalleryListing({
        section: "Images",
        searchTerm: "",
        sortOrder: "DESC",
        orderBy: "modificationDate",
      });

      React.useEffect(() => {
        FetchingData.getSuccessValue(galleryListing).do((listing) => {
          if (listing.tag === "list") selection.append(listing.list[2]);
        });
      }, [galleryListing]);

      return FetchingData.getSuccessValue(galleryListing)
        .map((listing) => {
          if (listing.tag === "empty") return null;
          // only render the carousel once something is selected
          // so that we can test the default file is the selected one
          if (selection.isEmpty) return null;
          return <Carousel listing={listing} key={null} />;
        })
        .orElse(null);
    });

    mockAxios.onGet("/collaboraOnline/supportedExts").reply(200, { data: {} });
    mockAxios.onGet("/officeOnline/supportedExts").reply(200, { data: {} });
    mockAxios.onGet("/gallery/getUploadedFiles").reply(200, page1);

    render(
      <GallerySelection>
        <Wrapper />
      </GallerySelection>
    );

    await waitFor(() => {
      expect(screen.getByRole("img")).toHaveAttribute("src", "/Streamfile/444");
    });
  });
});
