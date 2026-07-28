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
    ...extra,
  },
});

const respondWith = (revisions: Array<unknown>) => {
  mockAxios.onGet(VERSIONS_URL).reply(200, {
    data: { revisions, revisionsCount: revisions.length },
  });
};

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
    const rows = screen.getAllByRole("row").slice(1);
    expect(rows).toHaveLength(3);
    expect(rows[0]).toHaveTextContent("Version 3");
    expect(rows[2]).toHaveTextContent("Version 1");
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
    const rows = screen.getAllByRole("row").slice(1);
    expect(rows[0]).toHaveTextContent("(current)");
    expect(rows[1]).not.toHaveTextContent("(current)");
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

  test("a version row links to that version's bytes, not the live file's", async () => {
    // the API's file endpoint takes no version, so historical bytes come from Streamfile
    respondWith([revision(10, 1)]);

    render(<VersionHistoryDialogStory />);

    await waitFor(() => {
      expect(screen.getByRole("link", { name: /Version 1/ })).toBeInTheDocument();
    });
    expect(screen.getByRole("link", { name: /Version 1/ })).toHaveAttribute("href", "/Streamfile/42?version=1");
  });

  test("an item with no recorded history says so rather than showing an empty table", async () => {
    respondWith([]);

    render(<VersionHistoryDialogStory />);

    await waitFor(() => {
      expect(screen.getByText(/No version history is available/)).toBeInTheDocument();
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

  test("an image version opens in the image previewer rather than downloading", async () => {
    respondWith([revision(10, 1)]);
    const openSpy = vi.spyOn(window, "open").mockImplementation(() => null);

    render(<VersionHistoryDialogStory file={galleryFile({ isImage: true, extension: "png" })} />);

    await waitFor(() => {
      expect(screen.getByRole("link", { name: /Version 1/ })).toBeInTheDocument();
    });
    await userEvent.click(screen.getByRole("link", { name: /Version 1/ }));

    // handled in-app by the image preview context, so no new window
    expect(openSpy).not.toHaveBeenCalled();
  });

  test("a version of a file with no previewer is downloaded", async () => {
    respondWith([revision(10, 1)]);
    const openSpy = vi.spyOn(window, "open").mockImplementation(() => null);

    // a .txt file has no image, PDF or (with aspose disabled) Aspose previewer
    render(<VersionHistoryDialogStory file={galleryFile({ extension: "txt" })} />);

    await waitFor(() => {
      expect(screen.getByRole("link", { name: /Version 1/ })).toBeInTheDocument();
    });
    await userEvent.click(screen.getByRole("link", { name: /Version 1/ }));

    expect(openSpy).toHaveBeenCalledWith("/Streamfile/42?version=1");
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
