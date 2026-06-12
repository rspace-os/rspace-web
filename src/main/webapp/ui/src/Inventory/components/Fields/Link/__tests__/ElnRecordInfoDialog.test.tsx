import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { cleanup, render, screen, waitFor } from "@/__tests__/customQueries";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../../theme";

// The per-type bodies are exercised by their own tests; here we only assert that
// the shell fetches record information, branches by global-id prefix, and renders
// the correct per-type body. Stub both so the shell test stays focused.
vi.mock("../DocumentSections", () => ({
  default: ({
    info,
    pinnedVersion,
  }: {
    info: { oid: { idString: string } };
    pinnedVersion?: number | null;
  }) => (
    <div
      data-testid="document-sections"
      data-globalid={info.oid.idString}
      data-pinned-version={pinnedVersion ?? ""}
    />
  ),
}));

vi.mock("../GallerySections", () => ({
  default: ({ info }: { info: { oid: { idString: string } } }) => (
    <div data-testid="gallery-sections" data-globalid={info.oid.idString} />
  ),
}));

import ElnRecordInfoDialog from "../ElnRecordInfoDialog";

beforeEach(() => {
  fetchMock.resetMocks();
  vi.clearAllMocks();
});

afterEach(cleanup);

function recordInfoResponse(
  overrides: Record<string, unknown> = {},
): string {
  return JSON.stringify({
    data: {
      id: 123,
      oid: { idString: "SD123" },
      name: "My experiment",
      type: "Structured Document",
      ownerFullName: "Ada Lovelace",
      creationDateWithClientTimezoneOffset: "2026-05-01 10:00 +0000",
      modificationDateWithClientTimezoneOffset: "2026-05-02 10:00 +0000",
      ...overrides,
    },
    error: null,
    success: true,
  });
}

function renderDialog(
  props: Partial<React.ComponentProps<typeof ElnRecordInfoDialog>> = {},
) {
  return render(
    <ThemeProvider theme={materialTheme}>
      <ElnRecordInfoDialog
        open
        globalId="SD123"
        onClose={vi.fn()}
        {...props}
      />
    </ThemeProvider>,
  );
}

describe("ElnRecordInfoDialog", () => {
  it("fetches record information using the numeric id derived from the global id", async () => {
    fetchMock.mockResponse(recordInfoResponse());

    renderDialog();

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalled();
    });
    const url = fetchMock.mock.calls[0][0] as string;
    expect(url).toContain("/workspace/getRecordInformation?recordId=123");
  });

  it("fetches the pinned version when versionPin is set", async () => {
    fetchMock.mockResponse(recordInfoResponse({ version: 4 }));

    renderDialog({ globalId: "SD123", versionPin: 4 });

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalled();
    });
    const url = fetchMock.mock.calls[0][0] as string;
    expect(url).toContain("recordId=123");
    expect(url).toContain("version=4");
  });

  it("does not send a version param when the link is not pinned", async () => {
    fetchMock.mockResponse(recordInfoResponse());

    renderDialog({ globalId: "SD123" });

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalled();
    });
    const url = fetchMock.mock.calls[0][0] as string;
    expect(url).not.toContain("version=");
  });

  it("renders the DocumentSections body for an SD target", async () => {
    fetchMock.mockResponse(recordInfoResponse());

    renderDialog({ globalId: "SD123" });

    const body = await screen.findByTestId("document-sections");
    expect(body).toBeInTheDocument();
    expect(body).toHaveAttribute("data-globalid", "SD123");
    expect(screen.queryByTestId("gallery-sections")).not.toBeInTheDocument();
  });

  it("forwards the pinned version into the document body", async () => {
    fetchMock.mockResponse(recordInfoResponse({ version: 4 }));

    renderDialog({ globalId: "SD123", versionPin: 4 });

    const body = await screen.findByTestId("document-sections");
    expect(body).toHaveAttribute("data-pinned-version", "4");
  });

  it("ignores a versionPin on a non-SD (NB) target", async () => {
    // NB/GL are not versionable; a stray pin must not send version= nor show version view.
    fetchMock.mockResponse(
      recordInfoResponse({ oid: { idString: "NB55" }, type: "Notebook" }),
    );

    renderDialog({ globalId: "NB55", versionPin: 9 });

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalled();
    });
    const url = fetchMock.mock.calls[0][0] as string;
    expect(url).not.toContain("version=");
    const body = await screen.findByTestId("document-sections");
    expect(body).toHaveAttribute("data-pinned-version", "");
  });

  it("renders the DocumentSections body for a notebook (NB) target", async () => {
    fetchMock.mockResponse(
      recordInfoResponse({ oid: { idString: "NB55" }, type: "Notebook" }),
    );

    renderDialog({ globalId: "NB55" });

    const body = await screen.findByTestId("document-sections");
    expect(body).toBeInTheDocument();
    expect(body).toHaveAttribute("data-globalid", "NB55");
  });

  it("renders the GallerySections body for a gallery (GL) target", async () => {
    fetchMock.mockResponse(
      recordInfoResponse({ oid: { idString: "GL9" }, type: "Image" }),
    );

    renderDialog({ globalId: "GL9" });

    const body = await screen.findByTestId("gallery-sections");
    expect(body).toBeInTheDocument();
    expect(body).toHaveAttribute("data-globalid", "GL9");
    expect(screen.queryByTestId("document-sections")).not.toBeInTheDocument();
  });

  it("shows an error message when the record cannot be loaded", async () => {
    fetchMock.mockResponse(
      JSON.stringify({
        data: null,
        error: { errorMessages: ["Record not found"] },
        success: false,
      }),
      { status: 404, statusText: "Not Found" },
    );

    renderDialog();

    expect(
      await screen.findByText(/not available.*permission|could not.*load|not found/i),
    ).toBeInTheDocument();
  });

  it("shows a version-specific error with a latest link when the pinned version cannot be loaded", async () => {
    fetchMock.mockResponse(
      JSON.stringify({
        data: null,
        error: { errorMessages: ["Version not found"] },
        success: false,
      }),
      { status: 404, statusText: "Not Found" },
    );

    renderDialog({ globalId: "SD599", versionPin: 4 });

    expect(
      await screen.findByText(/version 4 of SD599 is no longer available/i),
    ).toBeInTheDocument();
    const latestLink = screen.getByRole("link", { name: /latest version/i });
    expect(latestLink).toHaveAttribute("href", "/globalId/SD599");
  });

  it("links the Open button to /globalId/<globalId> in a new tab", async () => {
    fetchMock.mockResponse(recordInfoResponse());

    renderDialog({ globalId: "SD123" });

    const openLink = await screen.findByRole("link", { name: /^open$/i });
    expect(openLink).toHaveAttribute("href", "/globalId/SD123");
    expect(openLink).toHaveAttribute("target", "_blank");
  });

  it("opens a gallery target at its location in the Gallery, not a download", async () => {
    fetchMock.mockResponse(
      recordInfoResponse({ oid: { idString: "GL9" }, type: "Image" }),
    );

    renderDialog({ globalId: "GL9" });

    const openLink = await screen.findByRole("link", { name: /^open$/i });
    expect(openLink).toHaveAttribute("href", "/gallery/item/9");
  });

  it("renders nothing when closed", () => {
    fetchMock.mockResponse(recordInfoResponse());
    const { container } = renderDialog({ open: false });
    expect(container).toBeEmptyDOMElement();
  });

  it("retries the fetch when reopened after a failure", async () => {
    // First open fails, second open succeeds; the dialog instance stays mounted.
    fetchMock.mockResponseOnce(
      JSON.stringify({
        data: null,
        error: { errorMessages: ["Record not found"] },
        success: false,
      }),
      { status: 404, statusText: "Not Found" },
    );
    fetchMock.mockResponse(recordInfoResponse());

    const { rerender } = renderDialog({ open: true });
    expect(
      await screen.findByText(/not available.*permission|not found/i),
    ).toBeInTheDocument();

    // Close, then reopen the same instance.
    rerender(
      <ThemeProvider theme={materialTheme}>
        <ElnRecordInfoDialog open={false} globalId="SD123" onClose={vi.fn()} />
      </ThemeProvider>,
    );
    rerender(
      <ThemeProvider theme={materialTheme}>
        <ElnRecordInfoDialog open globalId="SD123" onClose={vi.fn()} />
      </ThemeProvider>,
    );

    // On reopen the cleared cache + remounted boundary refetch and succeed.
    expect(await screen.findByTestId("document-sections")).toBeInTheDocument();
  });
});
