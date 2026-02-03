import { test, describe, expect } from 'vitest';
import "@/__tests__/mocks/useOauthToken";
import React from "react";
import {
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import * as FetchingData from "../../../util/fetchingData";
import { useGalleryListing, type GalleryFile } from "../useGalleryListing";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import page1 from "./getUploadedFiles_1.json";
import page2 from "./getUploadedFiles_2.json";
const mockAxios = new MockAdapter(axios);
function WrapperComponent() {
  const listingOf = React.useMemo(
    () => ({
      tag: "section" as const,
      section: "Images" as const,
      path: [],
    }),
    []
  );
  const { galleryListing, refreshListing } = useGalleryListing({
    listingOf,
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
          asymmetricMatch: (params: URLSearchParams) =>
            params.get("pageNumber") === "0",
        },
      })
      .reply(200, page1)
      .onGet("/gallery/getUploadedFiles", {
        params: {
          asymmetricMatch: (params: URLSearchParams) =>
            params.get("pageNumber") === "1",
        },
      })
      .reply(200, page2);
    render(<WrapperComponent />);
    await waitFor(() => {
      expect(screen.getByRole("button", { name: /load more/i })).toBeVisible();
    });
    await user.click(screen.getByRole("button", { name: /load more/i }));
    await waitFor(() => {
      expect(
        screen.queryByRole("button", { name: /load more/i })
      ).not.toBeInTheDocument();
    });
    const getUploadedFilesCalls = mockAxios.history.get.filter(({ url }) =>
      /getUploadedFiles/.test(url ?? "")
    );
    expect(getUploadedFilesCalls.length).toBe(2);
    expect(getUploadedFilesCalls[0].params.get("pageNumber")).toBe("0");
    expect(getUploadedFilesCalls[1].params.get("pageNumber")).toBe("1");
  });
});
