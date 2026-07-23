import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import { HttpResponse } from "msw";
import type React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import { captureRequests } from "@/__tests__/mswRequestCapture";
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

function recordInfoResponse(overrides: Record<string, unknown> = {}) {
  return {
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
  };
}

function mockRecordInfo(response: () => Response = () => HttpResponse.json(recordInfoResponse())): Request[] {
  return captureRequests("get", "/workspace/getRecordInformation", response);
}

// Most tests just need the default success response; this satisfies that
// case for free, and tests wanting an error or a different payload override
// it with their own `mockRecordInfo(...)` call (MSW checks the most recently
// registered handler first, so the override takes precedence).
let requests: Request[] = [];

beforeEach(() => {
  vi.clearAllMocks();
  requests = mockRecordInfo();
});

afterEach(cleanup);

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
  const queryClient = new QueryClient();
  await renderWithRealI18n(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={materialTheme}>
        <ElnRecordInfoDialog open globalId="SD123" onClose={vi.fn()} {...props} />
      </ThemeProvider>
    </QueryClientProvider>,
    { resources: { inventory: inventoryEn }, defaultNS: "inventory" },
  );
  return { queryClient };
}

describe("ElnRecordInfoDialog", () => {
  it("fetches record information using the numeric id derived from the global id", async () => {
    renderDialog();

    await waitFor(() => {
      expect(requests).toHaveLength(1);
    });
    expect(new URL(requests[0].url).searchParams.get("recordId")).toBe("123");
  });

  it("fetches the pinned version when versionPin is set", async () => {
    const requests = mockRecordInfo(() => HttpResponse.json(recordInfoResponse({ version: 4 })));

    renderDialog({ globalId: "SD123", versionPin: 4 });

    await waitFor(() => {
      expect(requests).toHaveLength(1);
    });
    const searchParams = new URL(requests[0].url).searchParams;
    expect(searchParams.get("recordId")).toBe("123");
    expect(searchParams.get("version")).toBe("4");
  });

  it("does not send a version param when the link is not pinned", async () => {
    renderDialog({ globalId: "SD123" });

    await waitFor(() => {
      expect(requests).toHaveLength(1);
    });
    expect(new URL(requests[0].url).searchParams.has("version")).toBe(false);
  });

  it("renders the DocumentSections body for an SD target", async () => {
    renderDialog({ globalId: "SD123" });

    const body = await screen.findByTestId("document-sections");
    expect(body).toBeInTheDocument();
    expect(body).toHaveAttribute("data-globalid", "SD123");
    expect(screen.queryByTestId("gallery-sections")).not.toBeInTheDocument();
  });

  it("forwards the pinned version into the document body", async () => {
    mockRecordInfo(() => HttpResponse.json(recordInfoResponse({ version: 4 })));

    renderDialog({ globalId: "SD123", versionPin: 4 });

    const body = await screen.findByTestId("document-sections");
    expect(body).toHaveAttribute("data-pinned-version", "4");
  });

  it("ignores a versionPin on a non-SD (NB) target", async () => {
    // NB/GL are not versionable; a stray pin must not send version= nor show version view.
    const requests = mockRecordInfo(() =>
      HttpResponse.json(recordInfoResponse({ oid: { idString: "NB55" }, type: "Notebook" })),
    );

    renderDialog({ globalId: "NB55", versionPin: 9 });

    await waitFor(() => {
      expect(requests).toHaveLength(1);
    });
    expect(new URL(requests[0].url).searchParams.has("version")).toBe(false);
    const body = await screen.findByTestId("document-sections");
    expect(body).toHaveAttribute("data-pinned-version", "");
  });

  it("renders the DocumentSections body for a notebook (NB) target", async () => {
    mockRecordInfo(() => HttpResponse.json(recordInfoResponse({ oid: { idString: "NB55" }, type: "Notebook" })));

    renderDialog({ globalId: "NB55" });

    const body = await screen.findByTestId("document-sections");
    expect(body).toBeInTheDocument();
    expect(body).toHaveAttribute("data-globalid", "NB55");
  });

  it("renders the GallerySections body for a gallery (GL) target", async () => {
    mockRecordInfo(() => HttpResponse.json(recordInfoResponse({ oid: { idString: "GL9" }, type: "Image" })));

    renderDialog({ globalId: "GL9" });

    const body = await screen.findByTestId("gallery-sections");
    expect(body).toBeInTheDocument();
    expect(body).toHaveAttribute("data-globalid", "GL9");
    expect(screen.queryByTestId("document-sections")).not.toBeInTheDocument();
  });

  it("shows an error message when the record cannot be loaded", async () => {
    mockRecordInfo(() =>
      HttpResponse.json(
        {
          data: null,
          error: { errorMessages: ["Record not found"] },
          success: false,
        },
        { status: 404, statusText: "Not Found" },
      ),
    );

    renderDialog();

    expect(await screen.findByText("inventory:fields.link.elnInfoDialog.unavailable")).toBeInTheDocument();
  });

  it("shows a version-specific error with a latest link when the pinned version cannot be loaded", async () => {
    mockRecordInfo(() =>
      HttpResponse.json(
        {
          data: null,
          error: { errorMessages: ["Version not found"] },
          success: false,
        },
        { status: 404, statusText: "Not Found" },
      ),
    );

    await renderDialogWithRealI18n({ globalId: "SD599", versionPin: 4 });

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Version 4 of SD599 is no longer available.");
    const latestLink = within(alert).getByRole("link", { name: "View the latest version" });
    expect(latestLink).toHaveAttribute("href", "/globalId/SD599");
  });

  it("links the Open button to /globalId/<globalId> in a new tab", async () => {
    renderDialog({ globalId: "SD123" });

    const openLink = await screen.findByRole("link", { name: "common:actions.open" });
    expect(openLink).toHaveAttribute("href", "/globalId/SD123");
    expect(openLink).toHaveAttribute("target", "_blank");
  });

  it("opens a gallery target at its location in the Gallery, not a download", async () => {
    mockRecordInfo(() => HttpResponse.json(recordInfoResponse({ oid: { idString: "GL9" }, type: "Image" })));

    renderDialog({ globalId: "GL9" });

    const openLink = await screen.findByRole("link", { name: "common:actions.open" });
    expect(openLink).toHaveAttribute("href", "/gallery/item/9");
  });

  it("hides the Open button when the ELN target has been deleted", async () => {
    // a deleted ELN record only routes to an error page, so the dialog drops
    // Open; deleted Inventory targets (still viewable in the trash) keep theirs,
    // but they render through InventoryInfoDialog, which has no Open button
    renderDialog({ globalId: "SD123", targetDeleted: true });

    // the dialog still renders (Close present), but there is no Open affordance
    await screen.findByRole("button", { name: "common:actions.close" });
    expect(screen.queryByRole("link", { name: "common:actions.open" })).not.toBeInTheDocument();
  });

  it("hides the Open button when the ELN target is not readable (no access)", async () => {
    // an unreadable ELN target (shared then unshared) routes only to an error
    // page, so Open is dropped for the same reason as a deleted target
    renderDialog({ globalId: "SD123", noAccess: true });

    await screen.findByRole("button", { name: "common:actions.close" });
    expect(screen.queryByRole("link", { name: "common:actions.open" })).not.toBeInTheDocument();
  });

  it("renders nothing when closed", () => {
    const { container } = renderDialog({ open: false });
    expect(container).toBeEmptyDOMElement();
  });

  it("retries the fetch when reopened after a failure", async () => {
    // First open fails, second open succeeds; the dialog instance stays mounted.
    let requestCount = 0;
    mockRecordInfo(() => {
      requestCount += 1;
      return requestCount === 1
        ? HttpResponse.json(
            {
              data: null,
              error: { errorMessages: ["Record not found"] },
              success: false,
            },
            { status: 404, statusText: "Not Found" },
          )
        : HttpResponse.json(recordInfoResponse());
    });

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
