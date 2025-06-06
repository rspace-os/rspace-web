/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import userEvent from "@testing-library/user-event";
import ActionsMenu from "../ActionsMenu";
import {
  dummyId,
  type GalleryFile,
  Description,
} from "../../useGalleryListing";
import {
  GallerySelection,
  useGallerySelection,
} from "../../useGallerySelection";
import { ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "../../../../accentedTheme";
import "../../../../../__mocks__/matchMedia";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import Result from "../../../../util/result";
import { ACCENT_COLOR } from "../../../../assets/branding/irods";
import * as ArrayUtils from "../../../../util/ArrayUtils";
import { ExportSelection } from "../../../../Export/common";

jest.mock("../CallablePdfPreview", () => ({
  usePdfPreview: () => ({
    openPdfPreview: () => {},
  }),
}));

jest.mock("../MoveToIrods", () => ({
  __esModule: true,
  default: () => null,
}));

const mockAxios = new MockAdapter(axios);

mockAxios.onGet("/userform/ajax/inventoryOauthToken").reply(200, {
  data: "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODAiLCJpYXQiOjE3MzQzNDI5NTYsImV4cCI6MTczNDM0NjU1NiwicmVmcmVzaFRva2VuSGFzaCI6ImZlMTVmYTNkNWUzZDVhNDdlMzNlOWUzNDIyOWIxZWEyMzE0YWQ2ZTZmMTNmYTQyYWRkY2E0ZjE0Mzk1ODJhNGQifQ.HCKre3g_P1wmGrrrnQncvFeT9pAePFSc4UPuyP5oehI",
});

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

function SetSelection({ files }: { files: ReadonlyArray<GalleryFile> }) {
  const selection = useGallerySelection();
  React.useEffect(() => {
    selection.clear();
    for (const f of files) selection.append(f);
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - selection will not change
     */
  }, [files]);
  return null;
}

describe("ActionsMenu", () => {
  test("When the selected file isn't a folder, open should not be visible.", async () => {
    const user = userEvent.setup();

    mockAxios.onGet("/collaboraOnline/supportedExts").reply(200, { data: {} });
    mockAxios.onGet("/officeOnline/supportedExts").reply(200, { data: {} });
    mockAxios
      .onGet("/export/ajax/defaultPDFConfig")
      .reply(200, { data: { pageSize: "A4" } });
    mockAxios.onGet("/gallery/getUploadedFiles").reply(200, {
      data: {
        parentId: 1,
        items: {
          results: [],
        },
      },
    });

    render(
      <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
        <GallerySelection>
          <SetSelection
            files={[
              {
                deconstructor: () => {},
                id: dummyId(),
                globalId: "GF1",
                name: "Foo",
                extension: "txt",
                creationDate: new Date(),
                modificationDate: new Date(),
                type: "image",
                thumbnailUrl: "example.com",
                ownerName: "Joe Bloggs",
                description: new Description({ key: "empty" }),
                version: 1,
                size: 1024,
                path: [],
                pathAsString: () => "",
                isFolder: false,
                isSystemFolder: false,
                isImage: true,
                isSnippet: false,
                isSnippetFolder: false,
                transformFilename(f: (filename: string) => string) {
                  return f("Foo");
                },
                setName: () => {},
                setDescription: () => {},
                linkedDocuments: null,
                canOpen: Result.Error([new Error("I'm a folder")]),
                canDuplicate: Result.Ok(null),
                canDelete: Result.Ok(null),
                canRename: Result.Ok(null),
                canMoveToIrods: Result.Ok(null),
                canBeExported: Result.Ok(null),
                canBeMoved: Result.Ok(null),
                canUploadNewVersion: Result.Ok(null),
                canBeLoggedOutOf: Result.Ok(null),
                treeViewItemId: "GF1",
                key: "GF1",
              },
            ]}
          />
          <ActionsMenu
            refreshListing={() => Promise.resolve()}
            section="Images"
            folderId={{ tag: "success", value: dummyId() }}
          />
        </GallerySelection>
      </ThemeProvider>
    );

    await waitFor(() => {
      expect(screen.getByRole("button")).toBeVisible();
    });

    await user.click(screen.getByRole("button"));

    expect(
      screen.queryByRole("menuitem", { name: /open/i })
    ).not.toBeInTheDocument();
  });
  test("When the selected file is a folder, open should be visible.", async () => {
    const user = userEvent.setup();

    mockAxios.onGet("/collaboraOnline/supportedExts").reply(200, { data: {} });
    mockAxios.onGet("/officeOnline/supportedExts").reply(200, { data: {} });
    mockAxios
      .onGet("/export/ajax/defaultPDFConfig")
      .reply(200, { data: { pageSize: "A4" } });
    mockAxios.onGet("/gallery/getUploadedFiles").reply(200, {
      data: {
        parentId: 1,
        items: {
          results: [],
        },
      },
    });

    render(
      <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
        <GallerySelection>
          <SetSelection
            files={[
              {
                deconstructor: () => {},
                id: dummyId(),
                globalId: "GF1",
                name: "Foo",
                extension: "",
                creationDate: new Date(),
                modificationDate: new Date(),
                type: "folder",
                thumbnailUrl: "example.com",
                ownerName: "Joe Bloggs",
                description: new Description({ key: "empty" }),
                version: 1,
                size: 1024,
                path: [],
                pathAsString: () => "",
                isFolder: true,
                isSystemFolder: false,
                isImage: false,
                isSnippet: false,
                isSnippetFolder: false,
                transformFilename(f: (filename: string) => string) {
                  return f("Foo");
                },
                setName: () => {},
                setDescription: () => {},
                linkedDocuments: null,
                canOpen: Result.Ok(null),
                canDuplicate: Result.Ok(null),
                canDelete: Result.Ok(null),
                canRename: Result.Ok(null),
                canMoveToIrods: Result.Ok(null),
                canBeExported: Result.Ok(null),
                canBeMoved: Result.Ok(null),
                canUploadNewVersion: Result.Ok(null),
                canBeLoggedOutOf: Result.Ok(null),
                treeViewItemId: "GF1",
                key: "GF1",
              },
            ]}
          />
          <ActionsMenu
            refreshListing={() => Promise.resolve()}
            section="Images"
            folderId={{ tag: "success", value: dummyId() }}
          />
        </GallerySelection>
      </ThemeProvider>
    );

    await waitFor(() => {
      expect(screen.getByRole("button")).toBeVisible();
    });

    await user.click(screen.getByRole("button"));

    expect(screen.getByRole("menuitem", { name: /open/i })).toBeVisible();
  });
  test("When multiple files are selected and export chosen, the right call to /exportArchive should be made", async () => {
    const user = userEvent.setup();

    mockAxios.onGet("/collaboraOnline/supportedExts").reply(200, { data: {} });
    mockAxios.onGet("/officeOnline/supportedExts").reply(200, { data: {} });
    mockAxios
      .onGet("/export/ajax/defaultPDFConfig")
      .reply(200, { data: { pageSize: "A4" } });
    mockAxios.onGet("/gallery/getUploadedFiles").reply(200, {
      data: {
        parentId: 1,
        items: {
          results: [],
        },
      },
    });

    render(
      <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
        <GallerySelection>
          <SetSelection
            files={[
              {
                deconstructor: () => {},
                id: dummyId(),
                globalId: "GF1",
                name: "Foo",
                extension: "",
                creationDate: new Date(),
                modificationDate: new Date(),
                type: "folder",
                thumbnailUrl: "example.com",
                ownerName: "Joe Bloggs",
                description: new Description({ key: "empty" }),
                version: 1,
                size: 1024,
                path: [],
                pathAsString: () => "",
                isFolder: true,
                isSystemFolder: false,
                isImage: false,
                isSnippet: false,
                isSnippetFolder: false,
                transformFilename(f: (filename: string) => string) {
                  return f("Foo");
                },
                setName: () => {},
                setDescription: () => {},
                linkedDocuments: null,
                canOpen: Result.Error([new Error("I'm a folder")]),
                canDuplicate: Result.Ok(null),
                canDelete: Result.Ok(null),
                canRename: Result.Ok(null),
                canMoveToIrods: Result.Ok(null),
                canBeExported: Result.Ok(null),
                canBeMoved: Result.Ok(null),
                canUploadNewVersion: Result.Ok(null),
                canBeLoggedOutOf: Result.Ok(null),
                treeViewItemId: "GF1",
                key: "GF1",
              },
              {
                deconstructor: () => {},
                id: dummyId(),
                globalId: "GF2",
                name: "Foo",
                extension: "",
                creationDate: new Date(),
                modificationDate: new Date(),
                type: "folder",
                thumbnailUrl: "example.com",
                ownerName: "Joe Bloggs",
                description: new Description({ key: "empty" }),
                version: 1,
                size: 1024,
                path: [],
                pathAsString: () => "",
                isFolder: true,
                isSystemFolder: false,
                isImage: false,
                isSnippet: false,
                isSnippetFolder: false,
                transformFilename(f: (filename: string) => string) {
                  return f("Foo");
                },
                setName: () => {},
                setDescription: () => {},
                linkedDocuments: null,
                canOpen: Result.Error([new Error("I'm a folder")]),
                canDuplicate: Result.Ok(null),
                canDelete: Result.Ok(null),
                canRename: Result.Ok(null),
                canMoveToIrods: Result.Ok(null),
                canBeExported: Result.Ok(null),
                canBeMoved: Result.Ok(null),
                canUploadNewVersion: Result.Ok(null),
                canBeLoggedOutOf: Result.Ok(null),
                treeViewItemId: "GF2",
                key: "GF2",
              },
            ]}
          />
          <ActionsMenu
            refreshListing={() => Promise.resolve()}
            section="Images"
            folderId={{ tag: "success", value: dummyId() }}
          />
        </GallerySelection>
      </ThemeProvider>
    );

    await waitFor(() => {
      expect(screen.getByRole("button")).toBeVisible();
    });

    await user.click(screen.getByRole("button"));

    expect(screen.getByRole("menuitem", { name: /export/i })).toBeVisible();

    await user.click(screen.getByRole("menuitem", { name: /export/i }));

    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeVisible();
    });

    await user.click(
      screen.getByRole("radio", { name: /.ZIP bundle containing .XML files/ })
    );

    await user.click(screen.getByRole("button", { name: /next/i }));
    await user.click(screen.getByRole("button", { name: /export/i }));

    expect(
      JSON.parse(
        ArrayUtils.head(mockAxios.history.post)
          .flatMap(({ data }: { data?: string }) =>
            Result.fromNullable(data, new Error("data is missing"))
          )
          .orElse("")
      )
    ).toEqual(
      expect.objectContaining({
        exportSelection: expect.objectContaining({
          type: "selection",
          exportTypes: ["FOLDER", "FOLDER"],
          exportNames: ["Foo", "Foo"],
          exportIds: [
            expect.stringMatching(/[0-9]+/),
            expect.stringMatching(/[0-9]+/),
          ],
        }) as ExportSelection,
      })
    );
  });
});
