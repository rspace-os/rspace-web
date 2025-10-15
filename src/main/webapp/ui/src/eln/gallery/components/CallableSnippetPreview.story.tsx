import React from "react";
import {
  CallableSnippetPreview,
  useSnippetPreview,
} from "./CallableSnippetPreview";
import { GalleryFile } from "../useGalleryListing";
import Button from "@mui/material/Button";
import Result from "../../../util/result";

const mockSnippetFile: GalleryFile = {
  id: 123,
  globalId: "GL123",
  name: "test-snippet.html",
  key: "snippet-123",
  extension: "html",
  creationDate: new Date("2024-01-01"),
  modificationDate: new Date("2024-01-15"),
  type: "snippet",
  thumbnailUrl: "",
  ownerName: "Test User",
  description: "A test HTML snippet",
  size: 1024,
  version: 1,
  originalImageId: null,
  path: [],
  pathAsString: () => "/snippets",
  downloadHref: async () => "/download/123",
  isFolder: false,
  isSystemFolder: false,
  isImage: false,
  isSnippet: true,
  isSnippetFolder: false,
  transformFilename: (f) => f("test-snippet"),
  setName: () => {},
  setDescription: () => {},
  linkedDocuments: null,
  metadata: {},
  canOpen: Result.Ok(null),
  canDuplicate: Result.Ok(null),
  canDelete: Result.Ok(null),
  canRename: Result.Ok(null),
  canMoveToIrods: Result.Error([new Error("Not supported")]),
  canBeExported: Result.Ok(null),
  canBeMoved: Result.Ok(null),
  canUploadNewVersion: Result.Ok(null),
  canBeLoggedOutOf: Result.Error([new Error("Not applicable")]),
  deconstructor: () => {},
} as GalleryFile;

function TestComponent() {
  const { openSnippetPreview } = useSnippetPreview();

  return (
    <Button onClick={() => openSnippetPreview(mockSnippetFile)}>
      Open Snippet Preview
    </Button>
  );
}

export function CallableSnippetPreviewStory() {
  return (
    <CallableSnippetPreview>
      <TestComponent />
    </CallableSnippetPreview>
  );
}

export function CallableSnippetPreviewWithTableContent() {
  const mockFileWithTable: GalleryFile = {
    ...mockSnippetFile,
    name: "table-snippet.html",
    id: 124,
  };

  function TestComponentWithTable() {
    const { openSnippetPreview } = useSnippetPreview();

    return (
      <Button onClick={() => openSnippetPreview(mockFileWithTable)}>
        Open Table Snippet Preview
      </Button>
    );
  }

  return (
    <CallableSnippetPreview>
      <TestComponentWithTable />
    </CallableSnippetPreview>
  );
}

export function CallableSnippetPreviewWithError() {
  const mockFileWithError: GalleryFile = {
    ...mockSnippetFile,
    name: "error-snippet.html",
    id: 999,
  };

  function TestComponentWithError() {
    const { openSnippetPreview } = useSnippetPreview();

    return (
      <Button onClick={() => openSnippetPreview(mockFileWithError)}>
        Open Error Snippet Preview
      </Button>
    );
  }

  return (
    <CallableSnippetPreview>
      <TestComponentWithError />
    </CallableSnippetPreview>
  );
}
