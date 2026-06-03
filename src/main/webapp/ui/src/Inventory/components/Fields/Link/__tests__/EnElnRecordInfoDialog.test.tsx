import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../../theme";

// The per-type bodies are exercised by their own tests; here we only assert that
// the shell fetches record information, branches by global-id prefix, and renders
// the correct per-type body. Stub both so the shell test stays focused.
vi.mock("../DocumentSections", () => ({
  default: ({ info }: { info: { oid: { idString: string } } }) => (
    <div data-testid="document-sections" data-globalid={info.oid.idString} />
  ),
}));

vi.mock("../GallerySections", () => ({
  default: ({ info }: { info: { oid: { idString: string } } }) => (
    <div data-testid="gallery-sections" data-globalid={info.oid.idString} />
  ),
}));

import EnElnRecordInfoDialog from "../EnElnRecordInfoDialog";

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
  props: Partial<React.ComponentProps<typeof EnElnRecordInfoDialog>> = {},
) {
  return render(
    <ThemeProvider theme={materialTheme}>
      <EnElnRecordInfoDialog
        open
        globalId="SD123"
        onClose={vi.fn()}
        {...props}
      />
    </ThemeProvider>,
  );
}

describe("EnElnRecordInfoDialog", () => {
  it("fetches record information using the numeric id derived from the global id", async () => {
    fetchMock.mockResponse(recordInfoResponse());

    renderDialog();

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalled();
    });
    const url = fetchMock.mock.calls[0][0] as string;
    expect(url).toContain("/workspace/getRecordInformation?recordId=123");
  });

  it("renders the DocumentSections body for an SD target", async () => {
    fetchMock.mockResponse(recordInfoResponse());

    renderDialog({ globalId: "SD123" });

    const body = await screen.findByTestId("document-sections");
    expect(body).toBeInTheDocument();
    expect(body).toHaveAttribute("data-globalid", "SD123");
    expect(screen.queryByTestId("gallery-sections")).not.toBeInTheDocument();
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

  it("links the Open button to /globalId/<globalId> in a new tab", async () => {
    fetchMock.mockResponse(recordInfoResponse());

    renderDialog({ globalId: "SD123" });

    const openLink = await screen.findByRole("link", { name: /^open$/i });
    expect(openLink).toHaveAttribute("href", "/globalId/SD123");
    expect(openLink).toHaveAttribute("target", "_blank");
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
        <EnElnRecordInfoDialog open={false} globalId="SD123" onClose={vi.fn()} />
      </ThemeProvider>,
    );
    rerender(
      <ThemeProvider theme={materialTheme}>
        <EnElnRecordInfoDialog open globalId="SD123" onClose={vi.fn()} />
      </ThemeProvider>,
    );

    // On reopen the cleared cache + remounted boundary refetch and succeed.
    expect(await screen.findByTestId("document-sections")).toBeInTheDocument();
  });
});
