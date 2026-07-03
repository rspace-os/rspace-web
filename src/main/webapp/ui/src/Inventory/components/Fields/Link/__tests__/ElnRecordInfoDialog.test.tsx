import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type React from "react";
import { I18nextProvider } from "react-i18next";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@/__tests__/customQueries";
import { createTestI18n } from "@/__tests__/helpers/createTestI18n";
import inventoryEn from "@/modules/common/i18n/locales/en-US/inventory.json";
import materialTheme from "../../../../../theme";

// The per-type bodies are exercised by their own tests; here we only assert that
// the shell fetches record information, branches by global-id prefix, and renders
// the correct per-type body. Stub both so the shell test stays focused.
vi.mock("../DocumentSections", () => ({
  default: ({ info, pinnedVersion }: { info: { oid: { idString: string } }; pinnedVersion?: number | null }) => (
    <div data-testid="document-sections" data-globalid={info.oid.idString} data-pinned-version={pinnedVersion ?? ""} />
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

function recordInfoResponse(overrides: Record<string, unknown> = {}): string {
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

function renderDialog(props: Partial<React.ComponentProps<typeof ElnRecordInfoDialog>> = {}) {
  // The dialog relies on an ancestor QueryClient (App.tsx provides one in the
  // running app); supply a fresh client per render here to mirror that.
  const queryClient = new QueryClient();
  return {
    ...render(
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={materialTheme}>
          <ElnRecordInfoDialog open globalId="SD123" onClose={vi.fn()} {...props} />
        </ThemeProvider>
      </QueryClientProvider>,
    ),
    queryClient,
  };
}

async function renderDialogWithRealI18n(props: Partial<React.ComponentProps<typeof ElnRecordInfoDialog>> = {}) {
  const i18n = await createTestI18n({ inventory: inventoryEn }, "inventory");
  const queryClient = new QueryClient();
  render(
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={materialTheme}>
          <ElnRecordInfoDialog open globalId="SD123" onClose={vi.fn()} {...props} />
        </ThemeProvider>
      </QueryClientProvider>
    </I18nextProvider>,
  );
  return { queryClient };
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
    fetchMock.mockResponse(recordInfoResponse({ oid: { idString: "NB55" }, type: "Notebook" }));

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
    fetchMock.mockResponse(recordInfoResponse({ oid: { idString: "NB55" }, type: "Notebook" }));

    renderDialog({ globalId: "NB55" });

    const body = await screen.findByTestId("document-sections");
    expect(body).toBeInTheDocument();
    expect(body).toHaveAttribute("data-globalid", "NB55");
  });

  it("renders the GallerySections body for a gallery (GL) target", async () => {
    fetchMock.mockResponse(recordInfoResponse({ oid: { idString: "GL9" }, type: "Image" }));

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

    expect(await screen.findByText("inventory:fields.link.elnInfoDialog.unavailable")).toBeInTheDocument();
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

    await renderDialogWithRealI18n({ globalId: "SD599", versionPin: 4 });

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Version 4 of SD599 is no longer available.");
    const latestLink = within(alert).getByRole("link", { name: "View the latest version" });
    expect(latestLink).toHaveAttribute("href", "/globalId/SD599");
  });

  it("links the Open button to /globalId/<globalId> in a new tab", async () => {
    fetchMock.mockResponse(recordInfoResponse());

    renderDialog({ globalId: "SD123" });

    const openLink = await screen.findByRole("link", { name: "common:actions.open" });
    expect(openLink).toHaveAttribute("href", "/globalId/SD123");
    expect(openLink).toHaveAttribute("target", "_blank");
  });

  it("opens a gallery target at its location in the Gallery, not a download", async () => {
    fetchMock.mockResponse(recordInfoResponse({ oid: { idString: "GL9" }, type: "Image" }));

    renderDialog({ globalId: "GL9" });

    const openLink = await screen.findByRole("link", { name: "common:actions.open" });
    expect(openLink).toHaveAttribute("href", "/gallery/item/9");
  });

  it("hides the Open button when the ELN target has been deleted", async () => {
    // a deleted ELN record only routes to an error page, so the dialog drops
    // Open; deleted Inventory targets (still viewable in the trash) keep theirs,
    // but they render through InventoryInfoDialog, which has no Open button
    fetchMock.mockResponse(recordInfoResponse());

    renderDialog({ globalId: "SD123", targetDeleted: true });

    // the dialog still renders (Close present), but there is no Open affordance
    await screen.findByRole("button", { name: "common:actions.close" });
    expect(screen.queryByRole("link", { name: "common:actions.open" })).not.toBeInTheDocument();
  });

  it("hides the Open button when the ELN target is not readable (no access)", async () => {
    // an unreadable ELN target (shared then unshared) routes only to an error
    // page, so Open is dropped for the same reason as a deleted target
    fetchMock.mockResponse(recordInfoResponse());

    renderDialog({ globalId: "SD123", noAccess: true });

    await screen.findByRole("button", { name: "common:actions.close" });
    expect(screen.queryByRole("link", { name: "common:actions.open" })).not.toBeInTheDocument();
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

    const { rerender, queryClient } = renderDialog({ open: true });
    expect(await screen.findByText("inventory:fields.link.elnInfoDialog.unavailable")).toBeInTheDocument();

    // Close, then reopen the same instance (reusing the same QueryClient, as the
    // app-level provider would, so the errored query is refetched on remount).
    rerender(
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={materialTheme}>
          <ElnRecordInfoDialog open={false} globalId="SD123" onClose={vi.fn()} />
        </ThemeProvider>
      </QueryClientProvider>,
    );
    rerender(
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={materialTheme}>
          <ElnRecordInfoDialog open globalId="SD123" onClose={vi.fn()} />
        </ThemeProvider>
      </QueryClientProvider>,
    );

    // On reopen the body remounts and React Query refetches the errored query,
    // which now succeeds.
    expect(await screen.findByTestId("document-sections")).toBeInTheDocument();
  });
});
