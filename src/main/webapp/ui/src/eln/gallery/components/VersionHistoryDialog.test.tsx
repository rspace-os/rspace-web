import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import { expectAccessible } from "@/__tests__/accessibility";
import axios from "@/common/axios";
import { galleryFile, VersionHistoryDialogStory } from "./VersionHistoryDialog.story";

const mockAxios = new MockAdapter(axios);

const VERSIONS_URL = "/gallery/ajax/versionHistory/42";

const revision = (revisionId: number, version: number, extra: Record<string, unknown> = {}) => ({
  revisionId,
  revisionType: "MOD",
  record: {
    version,
    lastModified: "2026-06-11T09:30:00Z",
    modifiedByFullName: "Alice Smith",
    size: 1024,
    name: "assay.txt",
    ...extra,
  },
});

const respondWith = (revisions: Array<unknown>) => {
  mockAxios.onGet(VERSIONS_URL).reply(200, {
    data: { revisions, revisionsCount: revisions.length },
  });
};

/*
 * Tests run with i18n in cimode, so t() yields the namespaced key and applies no
 * interpolation. Every version label therefore renders identically, and a row
 * has to be identified by its test id rather than by the version it shows.
 */
const CURRENT = "gallery:actionsMenu.versionHistory.versionCurrent";
const VIEWING = "gallery:actionsMenu.versionHistory.versionViewing";

const bodyRows = () => screen.getAllByRole("row").slice(1);

const rowFor = (version: number): HTMLElement => {
  const row = bodyRows().find((r) => r.getAttribute("data-test-id") === `VersionHistory-row-${version}`);
  if (!row) throw new Error(`No row for version ${version}`);
  return row;
};

const linkFor = (version: number) => within(rowFor(version)).getByRole("link");

beforeEach(() => {
  mockAxios.reset();
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe("VersionHistoryDialog", () => {
  test("lists a row per version, newest first", async () => {
    respondWith([revision(10, 1), revision(20, 2), revision(30, 3)]);

    render(<VersionHistoryDialogStory />);

    await waitFor(() => {
      expect(screen.getByRole("table")).toBeInTheDocument();
    });
    expect(bodyRows().map((r) => r.getAttribute("data-test-id"))).toEqual([
      "VersionHistory-row-3",
      "VersionHistory-row-2",
      "VersionHistory-row-1",
    ]);
  });

  test("collapses several revisions of one version into a single row", async () => {
    // a non-version-bumping edit must not look like a duplicate version
    respondWith([revision(10, 1), revision(20, 1), revision(30, 2)]);

    render(<VersionHistoryDialogStory />);

    await waitFor(() => {
      expect(screen.getByRole("table")).toBeInTheDocument();
    });
    expect(screen.getAllByRole("row").slice(1)).toHaveLength(2);
  });

  test("marks the live version as current", async () => {
    respondWith([revision(10, 2), revision(20, 3)]);

    render(<VersionHistoryDialogStory file={galleryFile({ version: 3 })} />);

    await waitFor(() => {
      expect(screen.getByRole("table")).toBeInTheDocument();
    });
    expect(rowFor(3)).toHaveTextContent(CURRENT);
    expect(rowFor(2)).not.toHaveTextContent(CURRENT);
  });

  test("shows each version's own filename, which need not match the live one", async () => {
    // a new version can replace the file with one of a different name
    respondWith([revision(10, 1, { name: "first-draft.tiff" }), revision(20, 2, { name: "final.png" })]);

    render(<VersionHistoryDialogStory file={galleryFile({ name: "final.png", version: 2 })} />);

    await waitFor(() => {
      expect(screen.getByRole("table")).toBeInTheDocument();
    });
    const rows = screen.getAllByRole("row").slice(1);
    expect(rows[0]).toHaveTextContent("final.png");
    expect(rows[1]).toHaveTextContent("first-draft.tiff");
  });

  test("shows who changed each version, when, and how big it was", async () => {
    respondWith([
      revision(10, 1, {
        modifiedByFullName: "Bob Jones",
        size: 918,
      }),
    ]);

    render(<VersionHistoryDialogStory />);

    await waitFor(() => {
      expect(screen.getByRole("table")).toBeInTheDocument();
    });
    const row = screen.getAllByRole("row")[1];
    expect(row).toHaveTextContent("Bob Jones");
    expect(within(row).getByText(/918 B/)).toBeInTheDocument();
  });

  test("a version row links to that version's pinned view, so the URL can be copied", async () => {
    respondWith([revision(10, 1), revision(20, 3)]);

    render(<VersionHistoryDialogStory file={galleryFile({ version: 3 })} />);

    await waitFor(() => {
      expect(screen.getByRole("table")).toBeInTheDocument();
    });
    expect(linkFor(1)).toHaveAttribute("href", "/gallery/item/42/1");
  });

  test("the live version links to the item view, with no version segment to redirect from", async () => {
    respondWith([revision(10, 1), revision(20, 3)]);

    render(<VersionHistoryDialogStory file={galleryFile({ version: 3 })} />);

    await waitFor(() => {
      expect(screen.getByRole("table")).toBeInTheDocument();
    });
    expect(linkFor(3)).toHaveAttribute("href", "/gallery/item/42");
  });

  test("clicking a version navigates in-app and closes the dialog", async () => {
    respondWith([revision(10, 1), revision(20, 3)]);
    const navigate = vi.fn();
    const onClose = vi.fn();

    render(<VersionHistoryDialogStory file={galleryFile({ version: 3 })} navigate={navigate} onClose={onClose} />);

    await waitFor(() => {
      expect(screen.getByRole("table")).toBeInTheDocument();
    });
    await userEvent.click(linkFor(1));

    expect(navigate).toHaveBeenCalledWith("/gallery/item/42/1");
    // the route is shared with the live view, so nothing unmounts the dialog for us
    expect(onClose).toHaveBeenCalled();
  });

  test("on a pinned view the shown version is marked as viewed and the newest as current", async () => {
    // file.version is the pinned version here, so it must not be read as the live one
    respondWith([revision(10, 1), revision(20, 3)]);

    render(<VersionHistoryDialogStory file={galleryFile({ version: 1, pinnedVersion: 1 })} />);

    await waitFor(() => {
      expect(screen.getByRole("table")).toBeInTheDocument();
    });
    expect(rowFor(3)).toHaveTextContent(CURRENT);
    expect(rowFor(1)).toHaveTextContent(VIEWING);
    expect(rowFor(1)).not.toHaveTextContent(CURRENT);
  });

  test("an item with no recorded history says so rather than showing an empty table", async () => {
    respondWith([]);

    render(<VersionHistoryDialogStory />);

    await waitFor(() => {
      expect(screen.getByText("gallery:actionsMenu.versionHistory.none")).toBeInTheDocument();
    });
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  test("shows an error message when the history cannot be loaded", async () => {
    mockAxios.onGet(VERSIONS_URL).reply(500);

    render(<VersionHistoryDialogStory />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });
    expect(screen.getByRole("alert")).toHaveTextContent(/Could not load version history|Request failed/);
  });

  test("a 200 carrying an error envelope is shown as an error, not as an empty history", async () => {
    mockAxios.onGet(VERSIONS_URL).reply(200, {
      data: null,
      error: { errorMessages: ["Resource is not authorized"] },
    });

    render(<VersionHistoryDialogStory />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });
    expect(screen.getByRole("alert")).toHaveTextContent("Resource is not authorized");
    expect(screen.queryByText(/No version history is available/)).not.toBeInTheDocument();
  });

  test("does not fetch anything while closed", async () => {
    respondWith([revision(10, 1)]);

    render(<VersionHistoryDialogStory open={false} />);

    await waitFor(() => {
      expect(mockAxios.history.get).toHaveLength(0);
    });
  });

  test("the dialog is accessible", async () => {
    respondWith([revision(10, 1), revision(20, 2)]);

    const { container } = render(<VersionHistoryDialogStory />);

    await waitFor(() => {
      expect(screen.getByRole("table")).toBeInTheDocument();
    });
    await expectAccessible(container);
  });
});
