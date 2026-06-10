import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { cleanup, render, screen, waitFor } from "@/__tests__/customQueries";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../../theme";

const { mockGetFolderTree } = vi.hoisted(() => ({
  mockGetFolderTree: vi.fn(),
}));

vi.mock("@/hooks/api/useFolders", () => ({
  default: () => ({
    getFolderTree: mockGetFolderTree,
    getFolder: vi.fn(),
    createFolder: vi.fn(),
  }),
}));

import ElnFolderBrowser from "../ElnFolderBrowser";

type Node = { id: number; globalId: string; name: string; type: string };

const ROOTS: ReadonlyArray<Node> = [
  { id: 100, globalId: "FL100", name: "Projects", type: "FOLDER" },
  { id: 200, globalId: "NB200", name: "Lab NB", type: "NOTEBOOK" },
  { id: 300, globalId: "SD300", name: "Protocol doc", type: "DOCUMENT" },
  // the folder-tree endpoint bundles Gallery/MEDIA files in with documents
  { id: 400, globalId: "GL400", name: "lemmings.gif", type: "MEDIA" },
];

function wireFolderTree() {
  mockGetFolderTree.mockImplementation(({ id }: { id?: number }) => {
    let records: ReadonlyArray<Node> = [];
    if (id === undefined) records = ROOTS;
    else if (id === 100)
      records = [{ id: 301, globalId: "SD301", name: "Sub doc", type: "DOCUMENT" }];
    else if (id === 200)
      records = [
        { id: 201, globalId: "SD201", name: "NB entry", type: "DOCUMENT" },
      ];
    return Promise.resolve({
      totalHits: records.length,
      pageNumber: 0,
      records,
    });
  });
}

function renderBrowser(
  onSelectionChange: (
    s: { globalId: string; name: string; type: string } | null,
  ) => void,
): void {
  render(
    <ThemeProvider theme={materialTheme}>
      <ElnFolderBrowser onSelectionChange={onSelectionChange} />
    </ThemeProvider>,
  );
}

describe("ElnFolderBrowser", () => {
  beforeEach(() => {
    mockGetFolderTree.mockReset();
    wireFolderTree();
  });
  afterEach(cleanup);

  it("requests the workspace tree including documents, notebooks and folders", async () => {
    renderBrowser(vi.fn());
    await waitFor(() => expect(mockGetFolderTree).toHaveBeenCalled());
    const firstCallArg = mockGetFolderTree.mock.calls[0][0] as {
      typesToInclude: Set<string>;
    };
    expect([...firstCallArg.typesToInclude].sort()).toEqual([
      "document",
      "folder",
      "notebook",
    ]);
  });

  it("renders documents, notebooks and folders at the root", async () => {
    renderBrowser(vi.fn());
    expect(
      await screen.findByRole("treeitem", { name: /protocol doc/i }),
    ).toBeInTheDocument();
    expect(screen.getByRole("treeitem", { name: /lab nb/i })).toBeInTheDocument();
    expect(
      screen.getByRole("treeitem", { name: /projects/i }),
    ).toBeInTheDocument();
  });

  it("reports a document selection when its label is clicked", async () => {
    const onSelectionChange = vi.fn();
    renderBrowser(onSelectionChange);
    const user = userEvent.setup();
    await user.click(await screen.findByText(/protocol doc/i));
    expect(onSelectionChange).toHaveBeenCalledWith({
      globalId: "SD300",
      name: "Protocol doc",
      type: "DOCUMENT",
    });
  });

  it("reports a notebook selection when its label is clicked", async () => {
    const onSelectionChange = vi.fn();
    renderBrowser(onSelectionChange);
    const user = userEvent.setup();
    await user.click(await screen.findByText(/lab nb/i));
    expect(onSelectionChange).toHaveBeenCalledWith({
      globalId: "NB200",
      name: "Lab NB",
      type: "NOTEBOOK",
    });
  });

  it("reveals a notebook's contents on click and lets an entry be selected", async () => {
    const onSelectionChange = vi.fn();
    renderBrowser(onSelectionChange);
    const user = userEvent.setup();

    // clicking the notebook selects it and expands it to reveal its entries
    await user.click(await screen.findByText(/lab nb/i));
    const entry = await screen.findByText(/nb entry/i);

    await user.click(entry);
    expect(onSelectionChange).toHaveBeenLastCalledWith({
      globalId: "SD201",
      name: "NB entry",
      type: "DOCUMENT",
    });
  });

  it("reports a Gallery file selection when its label is clicked", async () => {
    const onSelectionChange = vi.fn();
    renderBrowser(onSelectionChange);
    const user = userEvent.setup();
    await user.click(await screen.findByText(/lemmings\.gif/i));
    expect(onSelectionChange).toHaveBeenCalledWith({
      globalId: "GL400",
      name: "lemmings.gif",
      type: "MEDIA",
    });
  });

  it("reports no selection for a folder (navigate-only)", async () => {
    const onSelectionChange = vi.fn();
    renderBrowser(onSelectionChange);
    const user = userEvent.setup();
    await user.click(await screen.findByText("Projects"));
    expect(onSelectionChange).toHaveBeenCalledWith(null);
  });
});
