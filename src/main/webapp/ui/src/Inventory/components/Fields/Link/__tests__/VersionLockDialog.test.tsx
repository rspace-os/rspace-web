import { ThemeProvider } from "@mui/material/styles";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse } from "msw";
import type React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { captureRequests } from "@/__tests__/mswRequestCapture";
import materialTheme from "../../../../../theme";

// Every request in this file is mocked at the network layer with MSW, including the inventory
// ones: the real InvApiService is used so its URL is asserted rather than assumed. It gates each
// call on the auth store having finished synchronising, which never happens in a unit test
// (`isSynchronizing` starts true and only flips after authenticating), so the store is stubbed.
// This is not a network mock; the request it then makes is served by MSW below.
vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({ authStore: { isSynchronizing: false } }),
}));

import VersionLockDialog from "../VersionLockDialog";

// Request paths the dialog fetches from, one per target kind. The inventory path carries a
// trailing slash because ApiServiceBase.get builds `${resource}/${slug}` with an empty slug.
const INVENTORY_REVISIONS_PATH = "/api/inventory/v1/:recordType/:id/revisions/";
const ELN_REVISIONS_PATH = "/workspace/revisionHistory/ajax/:id/versions";
const GALLERY_REVISIONS_PATH = "/gallery/ajax/versionHistory/:id";

// The inventory /revisions endpoint returns Envers audit rows. Each carries the audit
// `revisionId` AND the user-facing `record.version`. Non-version-bumping edits create several
// revisions sharing one version (here revisions 10 and 11 are both version 1), so the picker
// must collapse to one row per user-facing version and pin the version, not the revisionId.
const revisionsBody = {
  revisions: [
    {
      revisionId: 10,
      revisionType: "MOD",
      record: {
        id: 42,
        globalId: "SA42",
        name: "Sample foo",
        version: 1,
        lastModified: "2026-01-15T10:00:00Z",
      },
    },
    {
      revisionId: 11,
      revisionType: "MOD",
      record: {
        id: 42,
        globalId: "SA42",
        name: "Sample foo edited",
        version: 1,
        lastModified: "2026-01-20T10:00:00Z",
      },
    },
    {
      revisionId: 22,
      revisionType: "MOD",
      record: {
        id: 42,
        globalId: "SA42",
        name: "Sample foo v2",
        version: 2,
        lastModified: "2026-02-15T10:00:00Z",
      },
    },
  ],
  revisionsCount: 3,
};

// The ELN revisions endpoint (/workspace/revisionHistory/ajax/{id}/versions) returns
// { data: RevisionRecord[] }, where each record has a document `version` number and a
// separate audit `revision` id (mirrors tinyMCE/InternalLink.tsx).
const elnRevisionsBody = {
  data: [
    {
      version: 1,
      revision: 101,
      name: "My document",
      oid: { idString: "SD55" },
      ownerId: 1,
      ownerFullName: "Owner One",
      modificationDate: "2026-01-10T10:00:00Z",
    },
    {
      version: 2,
      revision: 202,
      name: "My document",
      oid: { idString: "SD55" },
      ownerId: 1,
      ownerFullName: "Owner One",
      modificationDate: "2026-02-10T10:00:00Z",
    },
  ],
};

// A soft-deleted SD document's history includes the final content revision AND the
// soft-delete MOD revision, which share the same document version number (a delete does
// not bump the version). The endpoint returns both rows, so the picker must collapse them
// to one row per version (like the inventory path) rather than listing the final version twice.
const elnDuplicateFinalVersionBody = {
  data: [
    {
      version: 1,
      revision: 101,
      modificationDate: "2026-01-10T10:00:00Z",
    },
    {
      version: 2,
      revision: 202,
      modificationDate: "2026-02-10T10:00:00Z",
    },
    {
      // the soft-delete MOD: a later audit revision still at version 2
      version: 2,
      revision: 303,
      modificationDate: "2026-02-11T10:00:00Z",
    },
  ],
};

// The gallery endpoint (/gallery/ajax/versionHistory/{id}) returns the inventory revisions shape
// inside an AjaxReturnObject envelope. Revisions 30 and 31 are both version 1, so the picker must
// collapse them the same way the inventory path does.
const galleryRevisionsBody = {
  data: {
    revisions: [
      {
        revisionId: 30,
        revisionType: "MOD",
        record: { version: 1, lastModified: "2026-03-01T10:00:00Z", name: "photo.png" },
      },
      {
        revisionId: 31,
        revisionType: "MOD",
        record: { version: 1, lastModified: "2026-03-02T10:00:00Z", name: "photo.png" },
      },
      {
        revisionId: 40,
        revisionType: "MOD",
        record: { version: 2, lastModified: "2026-03-10T10:00:00Z", name: "photo-v2.png" },
      },
    ],
    revisionsCount: 3,
  },
};

/** Serves the inventory revisions body, and records the requests, so callers can assert the URL. */
function mockInventoryRevisions(response: () => Response = () => HttpResponse.json(revisionsBody)): Request[] {
  return captureRequests("get", INVENTORY_REVISIONS_PATH, response);
}

function mockElnRevisions(response: () => Response = () => HttpResponse.json(elnRevisionsBody)): Request[] {
  return captureRequests("get", ELN_REVISIONS_PATH, response);
}

function mockGalleryRevisions(response: () => Response = () => HttpResponse.json(galleryRevisionsBody)): Request[] {
  return captureRequests("get", GALLERY_REVISIONS_PATH, response);
}

function renderDialog(props: Partial<React.ComponentProps<typeof VersionLockDialog>> = {}) {
  return render(
    <ThemeProvider theme={materialTheme}>
      <VersionLockDialog
        open
        globalId="SA42"
        currentVersionPin={null}
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
        {...props}
      />
    </ThemeProvider>,
  );
}

function getVersionRadio(value: string): HTMLInputElement {
  const radio = screen.getAllByRole("radio").find((candidate) => candidate.getAttribute("value") === value);
  if (!(radio instanceof HTMLInputElement)) {
    throw new Error(`Version radio not found: ${value}`);
  }
  return radio;
}

describe("VersionLockDialog", () => {
  afterEach(() => {
    cleanup();
  });

  it("shows one row per user-facing version, collapsing multiple revisions of the same version", async () => {
    const requests = mockInventoryRevisions();

    renderDialog();

    await waitFor(() => {
      expect(getVersionRadio("1")).toBeInTheDocument();
      expect(getVersionRadio("2")).toBeInTheDocument();
    });
    // the raw Envers revision ids must NOT be presented as versions
    expect(screen.getAllByRole("radio").filter((radio) => radio.getAttribute("value") === "10")).toHaveLength(0);
    expect(screen.getAllByRole("radio").filter((radio) => radio.getAttribute("value") === "11")).toHaveLength(0);
    expect(screen.getAllByRole("radio").filter((radio) => radio.getAttribute("value") === "22")).toHaveLength(0);
    expect(new URL(requests[0].url).pathname).toBe("/api/inventory/v1/samples/42/revisions/");
  });

  it("fetches instrument revisions for an instrument link target", async () => {
    const requests = mockInventoryRevisions();

    renderDialog({ globalId: "IN42" });

    await waitFor(() => {
      expect(getVersionRadio("1")).toBeInTheDocument();
    });
    expect(new URL(requests[0].url).pathname).toBe("/api/inventory/v1/instruments/42/revisions/");
  });

  it("calls onConfirm with the chosen user-facing version, not the audit revisionId", async () => {
    mockInventoryRevisions();
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    renderDialog({ onConfirm });

    await waitFor(() => {
      expect(getVersionRadio("2")).toBeInTheDocument();
    });
    await user.click(getVersionRadio("2"));
    await user.click(screen.getByRole("button", { name: "inventory:fields.link.versionLock.lockToSelectedVersion" }));

    expect(onConfirm).toHaveBeenCalledWith(2);
  });

  it("re-syncs the selection when reopened after an abandoned edit", async () => {
    mockInventoryRevisions();
    const user = userEvent.setup();
    const stableProps = {
      globalId: "SA42",
      onConfirm: vi.fn(),
      onCancel: vi.fn(),
    };
    const { rerender } = render(
      <ThemeProvider theme={materialTheme}>
        <VersionLockDialog open currentVersionPin={null} {...stableProps} />
      </ThemeProvider>,
    );

    // abandon an edit: select Version 1, then close without confirming
    await waitFor(() => {
      expect(getVersionRadio("1")).toBeInTheDocument();
    });
    await user.click(getVersionRadio("1"));
    rerender(
      <ThemeProvider theme={materialTheme}>
        <VersionLockDialog open={false} currentVersionPin={null} {...stableProps} />
      </ThemeProvider>,
    );

    // reopen after the pin was saved as version 2 elsewhere: the abandoned
    // selection (Version 1) must not leak into the fresh open
    rerender(
      <ThemeProvider theme={materialTheme}>
        <VersionLockDialog open currentVersionPin={2} {...stableProps} />
      </ThemeProvider>,
    );

    // wait for the version rows to load before inspecting the radios
    await waitFor(() => {
      expect(getVersionRadio("2")).toBeInTheDocument();
    });
    const version2Radio = getVersionRadio("2");
    expect(version2Radio).toBeChecked();
  });

  it("calls onConfirm with null when the user selects 'latest' after a pin was in place", async () => {
    mockInventoryRevisions();
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    renderDialog({ onConfirm, currentVersionPin: 1 });

    await screen.findByText("common:versionLockPicker.latest");
    await user.click(getVersionRadio("__latest__"));
    await user.click(screen.getByRole("button", { name: "inventory:fields.link.versionLock.lockToSelectedVersion" }));

    expect(onConfirm).toHaveBeenCalledWith(null);
  });
});

describe("VersionLockDialog (ELN targets: SD documents, GL gallery files)", () => {
  afterEach(() => {
    cleanup();
  });

  it("fetches SD revisions from the ELN endpoint and shows a row per version", async () => {
    const requests = mockElnRevisions();

    renderDialog({ globalId: "SD55" });

    await waitFor(() => {
      expect(getVersionRadio("1")).toBeInTheDocument();
      expect(getVersionRadio("2")).toBeInTheDocument();
    });
    expect(new URL(requests[0].url).pathname).toBe("/workspace/revisionHistory/ajax/55/versions");
  });

  it("pins to the document version number, not the audit revision id", async () => {
    mockElnRevisions();
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    renderDialog({ globalId: "SD55", onConfirm });

    await waitFor(() => {
      expect(getVersionRadio("2")).toBeInTheDocument();
    });
    await user.click(getVersionRadio("2"));
    await user.click(screen.getByRole("button", { name: "inventory:fields.link.versionLock.lockToSelectedVersion" }));

    // version 2 maps to audit revision 202; the pin must be the version number (2).
    expect(onConfirm).toHaveBeenCalledWith(2);
  });

  it("collapses duplicate entries of the same version (a deleted doc's final version listed twice) to one row", async () => {
    mockElnRevisions(() => HttpResponse.json(elnDuplicateFinalVersionBody));

    renderDialog({ globalId: "SD55" });

    // wait for the version rows to load (version 1 is unique)
    await waitFor(() => {
      expect(getVersionRadio("1")).toBeInTheDocument();
    });
    // version 2 is returned twice (final edit + soft-delete MOD) but must render once
    const version2Radios = screen.getAllByRole("radio").filter((radio) => radio.getAttribute("value") === "2");
    expect(version2Radios).toHaveLength(1);
  });

  it("still shows the cannot-resolve fallback for unsupported ELN types (NB)", () => {
    const elnRequests = mockElnRevisions();
    const galleryRequests = mockGalleryRevisions();

    renderDialog({ globalId: "NB9" });

    expect(screen.getByText("inventory:fields.link.versionLock.cannotResolve")).toBeInTheDocument();
    expect(elnRequests).toHaveLength(0);
    expect(galleryRequests).toHaveLength(0);
  });

  it("fetches GL revisions from the gallery endpoint, collapsing revisions of one version", async () => {
    const requests = mockGalleryRevisions();

    renderDialog({ globalId: "GL77" });

    await waitFor(() => {
      expect(getVersionRadio("2")).toBeInTheDocument();
    });
    expect(screen.getAllByRole("radio").filter((radio) => radio.getAttribute("value") === "1")).toHaveLength(1);
    expect(new URL(requests[0].url).pathname).toBe("/gallery/ajax/versionHistory/77");
  });

  it("pins a GL target to the gallery item's version number", async () => {
    mockGalleryRevisions();
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    renderDialog({ globalId: "GL77", onConfirm });

    await waitFor(() => {
      expect(getVersionRadio("2")).toBeInTheDocument();
    });
    await user.click(getVersionRadio("2"));
    await user.click(screen.getByRole("button", { name: "inventory:fields.link.versionLock.lockToSelectedVersion" }));

    // version 2 maps to audit revision 40; the pin must be the version number (2)
    expect(onConfirm).toHaveBeenCalledWith(2);
  });

  it("reports the failure, and keeps the latest-only view, when the SD revisions fetch fails", async () => {
    mockElnRevisions(() => HttpResponse.error());

    renderDialog({ globalId: "SD55" });

    // SD is a supported target, so this is NOT the cannot-resolve fallback...
    expect(screen.queryByText("inventory:fields.link.versionLock.cannotResolve")).not.toBeInTheDocument();
    // ...the picker still renders with the Latest option and no version rows...
    expect(await screen.findByText("common:versionLockPicker.latest")).toBeInTheDocument();
    expect(screen.getAllByRole("radio").filter((radio) => radio.getAttribute("value") === "1")).toHaveLength(0);
    // ...but says so, rather than presenting an empty history as the truth.
    expect(screen.getByText("common:versionLockPicker.loadFailed")).toBeInTheDocument();
  });

  it("reports a failure, not an empty history, when the gallery endpoint returns an error envelope", async () => {
    // AjaxReturnObject signals failure with a 200 carrying {data: null, error}. Treating that as
    // "no versions" would tell the user this item cannot be pinned, when the list merely failed
    // to load.
    mockGalleryRevisions(() => HttpResponse.json({ data: null, error: { errorMessages: ["No such media file"] } }));

    renderDialog({ globalId: "GL77" });

    expect(await screen.findByText("common:versionLockPicker.loadFailed")).toBeInTheDocument();
    expect(screen.getAllByRole("radio").filter((radio) => radio.getAttribute("value") === "1")).toHaveLength(0);
  });
});
