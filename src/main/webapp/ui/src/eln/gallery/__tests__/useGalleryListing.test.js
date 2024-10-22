/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import userEvent from "@testing-library/user-event";
import * as FetchingData from "../../../util/fetchingData";
import { useGalleryListing } from "../useGalleryListing";
import MockAdapter from "axios-mock-adapter";
import * as axios from "axios";
import page1 from "./getUploadedFiles_1";
import page2 from "./getUploadedFiles_2";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

const mockAxios = new MockAdapter(axios);

function WrapperComponent() {
  const { galleryListing } = useGalleryListing({
    section: "Images",
    searchTerm: "",
    sortOrder: "DESC",
    orderBy: "modificationDate",
  });

  return FetchingData.match(galleryListing, {
    loading: () => "loading",
    error: () => "error",
    success: (listing) =>
      listing.tag === "empty" ? (
        listing.reason
      ) : (
        <div>
          There are {listing.list.length} results.
          {listing.loadMore
            .map((loadMore) => (
              <button key={null} onClick={loadMore}>
                Load more
              </button>
            ))
            .orElse(null)}
        </div>
      ),
  });
}

describe("useGalleryListing", () => {
  test("Load more button should disappear on last page", async () => {
    const user = userEvent.setup();

    mockAxios
      .onGet("/gallery/getUploadedFiles" /*, { params: { page: 0 }}*/)
      .reply(200, page1);

    //mockAxios
    //.onGet("/gallery/getUploadedFiles", {
    //asymmetricMatch: function (actual) {
    //console.debug(actual);
    //return true;
    //},
    //})
    //.reply(200, page1);

    //mockAxios
    //.onGet("/gallery/getUploadedFiles", expect.objectContaining({ page: 1 }))
    //.replyOnce(200, page2);

    render(<WrapperComponent />);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /load more/i })).toBeVisible();
    });

    user.click(screen.getByRole("button", { name: /load more/i }));

    await waitFor(() => {
      expect(
        screen.queryByRole("button", { name: /load more/i })
      ).not.toBeInTheDocument();
    });

    // I don't know why the first request is duplicated
    expect(mockAxios.history.get.length).toBe(3);
    expect(mockAxios.history.get[0].params.get("pageNumber")).toBe("0");
    expect(mockAxios.history.get[1].params.get("pageNumber")).toBe("0");
    expect(mockAxios.history.get[2].params.get("pageNumber")).toBe("1");
  });
});
