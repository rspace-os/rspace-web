import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { cleanup, render, screen, waitFor } from "@/__tests__/customQueries";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../../theme";

const { mockAxiosGet } = vi.hoisted(() => ({ mockAxiosGet: vi.fn() }));

vi.mock("@/common/axios", () => ({
  default: { get: mockAxiosGet },
}));

vi.mock("../ElnFolderBrowser", () => ({
  default: ({
    onPick,
  }: {
    onPick: (s: { globalId: string; name: string; type: string }) => void;
  }) => (
    <button
      type="button"
      data-testid="eln-folder-browser-pick"
      onClick={() =>
        onPick({ globalId: "SD9", name: "Browsed doc", type: "DOCUMENT" })
      }
    >
      browse pick
    </button>
  ),
}));

import ElnRecordPicker from "../ElnRecordPicker";

function mockSearchResults(
  records: ReadonlyArray<{ idString: string; name: string; type: string }>,
) {
  mockAxiosGet.mockResolvedValue({
    data: {
      data: records.map((r) => ({
        oid: { idString: r.idString },
        id: parseInt(r.idString.replace(/^[A-Z]{2}/, ""), 10),
        name: r.name,
        type: r.type,
      })),
      error: null,
    },
  });
}

function renderPicker(
  props: Partial<React.ComponentProps<typeof ElnRecordPicker>> = {},
) {
  return render(
    <ThemeProvider theme={materialTheme}>
      <ElnRecordPicker
        open
        onPick={vi.fn()}
        onCancel={vi.fn()}
        {...props}
      />
    </ThemeProvider>,
  );
}

async function search(term: string) {
  const user = userEvent.setup();
  await user.type(screen.getByRole("textbox", { name: /search the eln/i }), term);
  await user.click(screen.getByRole("button", { name: /^search$/i }));
}

describe("ElnRecordPicker", () => {
  beforeEach(() => {
    mockAxiosGet.mockReset();
  });
  afterEach(cleanup);

  it("queries the workspace simpleSearch endpoint with the entered term", async () => {
    mockSearchResults([]);
    renderPicker();
    await search("cell line");

    await waitFor(() =>
      expect(mockAxiosGet).toHaveBeenCalledWith(
        "/workspace/ajax/simpleSearch",
        { params: { searchQuery: "cell line" } },
      ),
    );
  });

  it("shows only ELN targets (SD, NB, GL) and filters out inventory and folders", async () => {
    mockSearchResults([
      { idString: "SD1", name: "Doc one", type: "Structured Document" },
      { idString: "NB2", name: "Notebook two", type: "Notebook" },
      { idString: "GL3", name: "Image three", type: "Image" },
      { idString: "SA9", name: "Sample nine", type: "Sample" },
      { idString: "FL4", name: "Folder four", type: "Folder" },
    ]);
    renderPicker();
    await search("thing");

    expect(await screen.findByText("Doc one")).toBeInTheDocument();
    expect(screen.getByText("Notebook two")).toBeInTheDocument();
    expect(screen.getByText("Image three")).toBeInTheDocument();
    expect(screen.queryByText("Sample nine")).not.toBeInTheDocument();
    expect(screen.queryByText("Folder four")).not.toBeInTheDocument();
  });

  it("returns the chosen target via onPick", async () => {
    const onPick = vi.fn();
    mockSearchResults([
      { idString: "SD1", name: "Doc one", type: "Structured Document" },
    ]);
    renderPicker({ onPick });
    await search("doc");

    const user = userEvent.setup();
    await user.click(await screen.findByText("Doc one"));

    expect(onPick).toHaveBeenCalledWith({
      globalId: "SD1",
      name: "Doc one",
      type: "Structured Document",
    });
  });

  it("surfaces a refine-your-search hint when the result set is capped at 50", async () => {
    mockSearchResults(
      Array.from({ length: 50 }, (_, i) => ({
        idString: `SD${i + 1}`,
        name: `Doc ${i + 1}`,
        type: "Structured Document",
      })),
    );
    renderPicker();
    await search("doc");

    expect(
      await screen.findByText(/refine your search/i),
    ).toBeInTheDocument();
  });

  it("switches to the Browse tab and returns a tree selection via onPick", async () => {
    const onPick = vi.fn();
    renderPicker({ onPick });
    const user = userEvent.setup();

    await user.click(screen.getByRole("tab", { name: /browse/i }));
    await user.click(screen.getByTestId("eln-folder-browser-pick"));

    expect(onPick).toHaveBeenCalledWith({
      globalId: "SD9",
      name: "Browsed doc",
      type: "DOCUMENT",
    });
  });

  it("shows the Search tab by default", () => {
    renderPicker();
    expect(
      screen.getByRole("textbox", { name: /search the eln/i }),
    ).toBeInTheDocument();
  });

  it("shows a no-results message when nothing ELN-linkable matches", async () => {
    mockSearchResults([
      { idString: "SA9", name: "Sample nine", type: "Sample" },
    ]);
    renderPicker();
    await search("sample");

    expect(
      await screen.findByText(/no matching eln documents, notebooks or gallery files/i),
    ).toBeInTheDocument();
  });
});
