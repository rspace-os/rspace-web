import { afterEach, describe, expect, test } from "vitest";
import "@/__tests__/__mocks__/useOauthToken";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import React from "react";
import axios from "@/common/axios";
import * as FetchingData from "../../../util/fetchingData";
import {
  Description,
  dummyId,
  Filestore,
  type GalleryFile,
  LocalGalleryFile,
  RemoteFile,
  useGalleryListing,
} from "../useGalleryListing";
import page1 from "./getUploadedFiles_1.json";

import page2 from "./getUploadedFiles_2.json";

const mockAxios = new MockAdapter(axios);
afterEach(() => {
  mockAxios.reset();
});

function WrapperComponent() {
  const listingOf = React.useMemo(
    () => ({
      tag: "section" as const,
      section: "Images" as const,
      path: [],
    }),
    [],
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
              // biome-ignore lint/a11y/useButtonType: initial biome migration
              <button key={null} onClick={() => void loadMore()}>
                Load more
              </button>
            ))
            .orElse(null)}
          {/** biome-ignore lint/a11y/useButtonType: initial biome migration */}
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

function OwnerFieldsWrapper() {
  const listingOf = React.useMemo(
    () => ({
      tag: "section" as const,
      section: "Images" as const,
      path: [],
    }),
    [],
  );
  const { galleryListing } = useGalleryListing({
    listingOf,
    searchTerm: "",
    sortOrder: "DESC",
    orderBy: "modificationDate",
  });

  return FetchingData.match(galleryListing, {
    loading: () => "loading",
    error: () => "error",
    success: (listing) => {
      if (listing.tag !== "list") return listing.reason;
      const [file] = listing.list as Array<GalleryFile>;
      return (
        <div>
          <span data-testid="owner-id">{String(file.ownerId)}</span>
          <span data-testid="owner-name">{file.ownerName}</span>
          <span data-testid="owner-username">{String(file.ownerUsername)}</span>
          <span data-testid="is-shared-folder">{String(file.isSharedFolder)}</span>
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
          asymmetricMatch: (params: URLSearchParams) => params.get("pageNumber") === "0",
        },
      })
      .reply(200, page1)
      .onGet("/gallery/getUploadedFiles", {
        params: {
          asymmetricMatch: (params: URLSearchParams) => params.get("pageNumber") === "1",
        },
      })

      .reply(200, page2);

    render(<WrapperComponent />);
    await waitFor(() => {
      expect(screen.getByRole("button", { name: /load more/i })).toBeVisible();
    });

    await user.click(screen.getByRole("button", { name: /load more/i }));
    await waitFor(() => {
      expect(screen.queryByRole("button", { name: /load more/i })).not.toBeInTheDocument();
    });
    const getUploadedFilesCalls = mockAxios.history.get.filter(({ url }) => /getUploadedFiles/.test(url ?? ""));
    const firstPageParams = getUploadedFilesCalls[0].params as URLSearchParams;
    const secondPageParams = getUploadedFilesCalls[1].params as URLSearchParams;
    expect(getUploadedFilesCalls.length).toBe(2);
    expect(firstPageParams.get("pageNumber")).toBe("0");
    expect(secondPageParams.get("pageNumber")).toBe("1");
  });

  test("null and missing owner fields should fall back safely", async () => {
    mockAxios.onGet("/gallery/getUploadedFiles").reply(200, {
      data: {
        parentId: 1,
        items: {
          totalHits: 1,
          totalPages: 1,
          results: [
            {
              id: 3,
              oid: { idString: "SD3" },
              name: "My Snippet",
              ownerId: null,
              ownerFullName: null,
              ownerUsername: null,
              description: null,
              creationDate: 1672531200,
              modificationDate: 1672531200,
              type: "Snippet",
              sharedFolder: null,
              systemFolder: null,
              extension: "txt",
              thumbnailId: null,
              size: 512,
              version: 1,
              originalImageOid: { idString: "SD3" },
            },
          ],
        },
      },
      error: null,
      success: true,
      errorMsg: null,
    });

    render(<OwnerFieldsWrapper />);

    await waitFor(() => {
      expect(screen.getByTestId("owner-id")).toHaveTextContent("null");
    });

    expect(screen.getByTestId("owner-name")).toHaveTextContent("Unknown owner");
    expect(screen.getByTestId("owner-username")).toHaveTextContent("null");
    expect(screen.getByTestId("is-shared-folder")).toHaveTextContent("false");
  });
});

function makeFilestore(filesystemType: string): Filestore {
  return new Filestore({
    id: 42,
    name: "My Filestore",
    filesystemId: 10,
    filesystemName: "My S3 Filesystem",
    filesystemType,
    canWrite: true,
  });
}

function makeRemoteFile({ folder, path }: { folder: boolean; path: ReadonlyArray<GalleryFile> }): RemoteFile {
  return new RemoteFile({
    nfsId: null,
    name: "test.jpg",
    folder,
    fileSize: 1024,
    modificationDate: new Date(),
    path,
    logicPath: "42:/test.jpg",
    token: "",
  });
}

describe("RemoteFile.canMoveToS3", () => {
  test("returns Ok for a non-folder file in an S3 filestore", () => {
    const file = makeRemoteFile({
      folder: false,
      path: [makeFilestore("S3")],
    });
    expect(file.canMoveToS3.isOk).toBe(true);
  });

  test("returns Error for a folder in an S3 filestore", () => {
    const file = makeRemoteFile({
      folder: true,
      path: [makeFilestore("S3")],
    });
    expect(file.canMoveToS3.isOk).toBe(false);
    expect(file.canMoveToS3.orElseGet(([e]) => e)).toMatchObject({
      message: expect.stringContaining("folder"),
    });
  });

  test("returns Error for a non-folder file in a non-S3 filestore", () => {
    const file = makeRemoteFile({
      folder: false,
      path: [makeFilestore("IRODS")],
    });
    expect(file.canMoveToS3.isOk).toBe(false);
    expect(file.canMoveToS3.orElseGet(([e]) => e)).toMatchObject({
      message: expect.stringContaining("S3 filestore"),
    });
  });

  test("returns Error when the parent is not a Filestore instance", () => {
    const nonFilestoreParent = makeRemoteFile({
      folder: true,
      path: [],
    });
    const file = makeRemoteFile({
      folder: false,
      path: [nonFilestoreParent],
    });
    expect(file.canMoveToS3.isOk).toBe(false);
  });
});

function makeLocalGalleryFile({ type = "image" }: { type?: string } = {}): LocalGalleryFile {
  return new LocalGalleryFile({
    id: dummyId(),
    globalId: "GF_LOCAL",
    name: "test.jpg",
    extension: "jpg",
    creationDate: new Date(),
    modificationDate: new Date(),
    description: new Description({ key: "empty" }),
    type,
    isSystemFolder: false,
    isSharedFolder: false,
    ownerId: 1,
    ownerName: "Test User",
    ownerUsername: "testuser",
    path: [],
    gallerySection: "Images",
    size: 1024,
    version: 1,
    thumbnailId: null,
    originalImageId: null,
    metadata: {},
    token: "",
  });
}

describe("LocalGalleryFile.canMoveToIrods", () => {
  test("returns Ok for a non-folder file", () => {
    const file = makeLocalGalleryFile();
    expect(file.canMoveToIrods.isOk).toBe(true);
  });

  test("returns Error for a folder", () => {
    const file = makeLocalGalleryFile({ type: "Folder" });
    expect(file.canMoveToIrods.isOk).toBe(false);
    expect(file.canMoveToIrods.orElseGet(([e]) => e)).toMatchObject({
      message: expect.stringContaining("folder"),
    });
  });
});

describe("LocalGalleryFile.canMoveToS3", () => {
  test("returns Ok for a non-folder file", () => {
    const file = makeLocalGalleryFile();
    expect(file.canMoveToS3.isOk).toBe(true);
  });

  test("returns Error for a folder", () => {
    const file = makeLocalGalleryFile({ type: "Folder" });
    expect(file.canMoveToS3.isOk).toBe(false);
    expect(file.canMoveToS3.orElseGet(([e]) => e)).toMatchObject({
      message: expect.stringContaining("folder"),
    });
  });
});
