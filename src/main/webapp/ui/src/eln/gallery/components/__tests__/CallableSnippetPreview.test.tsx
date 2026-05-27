import { beforeEach, describe, expect, test, vi } from "vitest";
import React from "react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import { render, screen, waitFor } from "@/__tests__/customQueries";
import Button from "@mui/material/Button";
import {
  CallableSnippetPreview,
  useSnippetPreview,
} from "../CallableSnippetPreview";
import { Description, GalleryFile } from "../../useGalleryListing";
import Result from "../../../../util/result";

vi.mock("@/hooks/auth/useOauthToken", () => ({
  __esModule: true,
  default: () => ({
    getToken: () => Promise.resolve("token"),
  }),
}));

const mockAxios = new MockAdapter(axios);

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
  ownerId: 1,
  ownerName: "Test User",
  ownerUsername: "testuser",
  description: Description.Present("A test HTML snippet"),
  size: 1024,
  version: 1,
  originalImageId: null,
  path: [],
  pathAsString: () => "/snippets",
  downloadHref: () => Promise.resolve("/download/123"),
  isFolder: false,
  isSystemFolder: false,
  isSharedFolder: false,
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
  treeViewItemId: "tree-item-123",
};

function TestComponent() {
  const { openSnippetPreview } = useSnippetPreview();

  return (
    <Button onClick={() => openSnippetPreview(mockSnippetFile)}>
      Open Snippet Preview
    </Button>
  );
}

function CallableSnippetPreviewStory() {
  return (
    <CallableSnippetPreview>
      <TestComponent />
    </CallableSnippetPreview>
  );
}

function CallableSnippetPreviewWithTableContent() {
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

function CallableSnippetPreviewWithError() {
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

describe("CallableSnippetPreview", () => {
  beforeEach(() => {
    mockAxios.reset();
  });

  test("opens the dialog and renders snippet content", async () => {
    const user = userEvent.setup();
    mockAxios.onGet("/api/v1/snippets/123/content").reply(200, "<p>Test snippet content</p>");

    render(<CallableSnippetPreviewStory />);

    await user.click(screen.getByRole("button", { name: /open snippet preview/i }));

    expect(await screen.findByRole("dialog")).toBeVisible();
    expect(await screen.findByText(/test snippet content/i)).toBeVisible();
  });

  test("closes the dialog when escape is pressed", async () => {
    const user = userEvent.setup();
    mockAxios.onGet("/api/v1/snippets/123/content").reply(200, "<p>Test snippet content</p>");

    render(<CallableSnippetPreviewStory />);

    await user.click(screen.getByRole("button", { name: /open snippet preview/i }));
    expect(await screen.findByRole("dialog")).toBeVisible();

    await user.keyboard("{Escape}");

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
  });

  test("renders table content", async () => {
    const user = userEvent.setup();
    mockAxios.onGet("/api/v1/snippets/124/content").reply(
      200,
      `<table><thead><tr><th>Header 1</th><th>Header 2</th><th>Header 3</th></tr></thead><tbody><tr><td>Cell 1</td><td>Cell 2</td><td>Cell 3</td></tr><tr><td>Cell 4</td><td>Cell 5</td><td>Cell 6</td></tr></tbody></table>`,
    );

    render(<CallableSnippetPreviewWithTableContent />);

    await user.click(
      screen.getByRole("button", { name: /open table snippet preview/i }),
    );

    const dialog = await screen.findByRole("dialog");
    expect(dialog).toBeVisible();
    expect(screen.getAllByRole("columnheader")).toHaveLength(3);
    expect(screen.getAllByRole("cell")).toHaveLength(6);
    expect(screen.getByRole("columnheader", { name: "Header 1" })).toBeVisible();
    expect(screen.getByRole("cell", { name: "Cell 1" })).toBeVisible();
  });

  test("renders an error message when loading fails", async () => {
    const user = userEvent.setup();
    mockAxios.onGet("/api/v1/snippets/999/content").reply(500, {
      message: "Failed to load snippet content",
    });

    render(<CallableSnippetPreviewWithError />);

    await user.click(screen.getByRole("button", { name: /open error snippet preview/i }));

    expect(
      await screen.findByText(/failed to load snippet content/i),
    ).toBeVisible();
  });

  test("is accessible when open", async () => {
    const user = userEvent.setup();
    mockAxios.onGet("/api/v1/snippets/123/content").reply(200, "<p>Test snippet content</p>");

    const { baseElement } = render(<CallableSnippetPreviewStory />);

    await user.click(screen.getByRole("button", { name: /open snippet preview/i }));
    await screen.findByText(/test snippet content/i);

    // @ts-expect-error toBeAccessible is provided by @sa11y/vitest
    // eslint-disable-next-line @typescript-eslint/no-unsafe-call
    await expect(baseElement).toBeAccessible();
  });
});
