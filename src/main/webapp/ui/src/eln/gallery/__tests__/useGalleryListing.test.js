/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, waitFor, act } from "@testing-library/react";
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

mockAxios.onGet("/userform/ajax/inventoryOauthToken").reply(200, {
  data: "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODAiLCJpYXQiOjE3MzQzNDI5NTYsImV4cCI6MTczNDM0NjU1NiwicmVmcmVzaFRva2VuSGFzaCI6ImZlMTVmYTNkNWUzZDVhNDdlMzNlOWUzNDIyOWIxZWEyMzE0YWQ2ZTZmMTNmYTQyYWRkY2E0ZjE0Mzk1ODJhNGQifQ.HCKre3g_P1wmGrrrnQncvFeT9pAePFSc4UPuyP5oehI",
});

function WrapperComponent() {
  const { galleryListing, refreshListing } = useGalleryListing({
    initialLocation: { tag: "section", section: "Images" },
    searchTerm: "",
    sortOrder: "DESC",
    orderBy: "modificationDate",
  });

  return FetchingData.match(galleryListing, {
    loading: () => "loading",
    error: () => "error",
    success: (listing) => {
      if (listing.tag === "empty") {
        return listing.reason;
      }
      return (
        <div>
          There are {listing.list.length} results.
          {listing.loadMore
            .map((loadMore) => (
              <button key={null} onClick={loadMore}>
                Load more
              </button>
            ))
            .orElse(null)}
          <button
            onClick={() => {
              void refreshListing();
            }}
          >
            Refresh
          </button>
        </div>
      );
    },
  });
}

describe("useGalleryListing", () => {
  test("Load more button should disappear on last page", async () => {
    const user = userEvent.setup();

    /*
     * The asymmetricMatch thing here is to match the URLSearchParams.
     * Ideally, we would be able to use expect.objectContaining, but
     * URLSearchParams isn't an object, its an instance of a class.
     * Therefore, we have to use a custom asymmetric matcher.
     * Rather than copy and paste this code, write a re-usable matcher for
     * URLSearchParams.
     */
    mockAxios
      .onGet("/gallery/getUploadedFiles", {
        params: {
          asymmetricMatch: (params) => params.get("pageNumber") === "0",
        },
      })
      .reply(200, page1)
      .onGet("/gallery/getUploadedFiles", {
        params: {
          asymmetricMatch: (params) => params.get("pageNumber") === "1",
        },
      })
      .reply(200, page2);

    render(<WrapperComponent />);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /load more/i })).toBeVisible();
    });

    await act(async () => {
      await user.click(screen.getByRole("button", { name: /load more/i }));
    });

    await waitFor(() => {
      expect(
        screen.queryByRole("button", { name: /load more/i })
      ).not.toBeInTheDocument();
    });

    const getUploadedFilesCalls = mockAxios.history.get.filter(({ url }) =>
      /getUploadedFiles/.test(url)
    );
    expect(getUploadedFilesCalls.length).toBe(2);
    expect(getUploadedFilesCalls[0].params.get("pageNumber")).toBe("0");
    expect(getUploadedFilesCalls[1].params.get("pageNumber")).toBe("1");
  });
});
