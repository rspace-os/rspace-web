/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import userEvent from "@testing-library/user-event";
import ActionsMenu from "../ActionsMenu";
import {
  DUMMY_ID,
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
import * as axios from "axios";
import Result from "../../../../util/result";

jest.mock("../CallablePdfPreview", () => ({
  usePdfPreview: () => ({
    openPdfPreview: () => {},
  }),
}));

const COLOR = {
  main: {
    hue: 0,
    saturation: 0,
    lightness: 0,
  },
  darker: {
    hue: 0,
    saturation: 0,
    lightness: 0,
  },
  contrastText: {
    hue: 0,
    saturation: 0,
    lightness: 0,
  },
  background: {
    hue: 0,
    saturation: 0,
    lightness: 0,
  },
  backgroundContrastText: {
    hue: 0,
    saturation: 0,
    lightness: 0,
  },
};

jest.mock("../MoveToIrods", () => ({
  __esModule: true,
  default: () => null,
  COLOR,
}));

const mockAxios = new MockAdapter(axios);

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

function SetSelection({ file }: {| file: GalleryFile |}) {
  const selection = useGallerySelection();
  React.useEffect(() => {
    selection.clear();
    selection.append(file);
  }, [file]);
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
      <ThemeProvider theme={createAccentedTheme(COLOR)}>
        <GallerySelection>
          <SetSelection
            file={{
              id: DUMMY_ID,
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
              transformFilename(f: (string) => string) {
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
              canBeShared: Result.Ok(null),
              canBeExported: Result.Ok(null),
            }}
          />
          <ActionsMenu
            refreshListing={() => {}}
            section="Images"
            folderId={{ tag: "success", value: DUMMY_ID }}
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
      <ThemeProvider theme={createAccentedTheme(COLOR)}>
        <GallerySelection>
          <SetSelection
            file={{
              id: DUMMY_ID,
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
              transformFilename(f: (string) => string) {
                return f("Foo");
              },
              setName: () => {},
              setDescription: () => {},
              linkedDocuments: null,
              canOpen: Result.Ok(() => {}),
              canDuplicate: Result.Ok(null),
              canDelete: Result.Ok(null),
              canRename: Result.Ok(null),
              canMoveToIrods: Result.Ok(null),
              canBeShared: Result.Ok(null),
              canBeExported: Result.Ok(null),
            }}
          />
          <ActionsMenu
            refreshListing={() => {}}
            section="Images"
            folderId={{ tag: "success", value: DUMMY_ID }}
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
});
