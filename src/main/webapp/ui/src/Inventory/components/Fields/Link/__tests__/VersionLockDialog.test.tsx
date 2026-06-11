import { afterEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { cleanup, render, screen } from "@/__tests__/customQueries";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../../theme";
import InvApiService from "../../../../../common/InvApiService";
import axios from "@/common/axios";
import { type AxiosResponse } from "axios";

vi.mock("../../../../../common/InvApiService", () => ({
  default: {
    get: vi.fn(),
  },
}));

vi.mock("@/common/axios", () => ({
  default: {
    get: vi.fn(),
  },
}));

import VersionLockDialog from "../VersionLockDialog";

// The inventory /revisions endpoint returns Envers audit rows. Each carries the audit
// `revisionId` AND the user-facing `record.version`. Non-version-bumping edits create several
// revisions sharing one version (here revisions 10 and 11 are both version 1), so the picker
// must collapse to one row per user-facing version and pin the version, not the revisionId.
const revisionsResponse = {
  data: {
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
  },
  status: 200,
  statusText: "OK",
  headers: {},
  config: {},
} as AxiosResponse;

// The ELN revisions endpoint (/workspace/revisionHistory/ajax/{id}/versions) returns
// { data: RevisionRecord[] }, where each record has a document `version` number and a
// separate audit `revision` id (mirrors tinyMCE/InternalLink.tsx).
const elnRevisionsResponse = {
  data: {
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
  },
  status: 200,
  statusText: "OK",
  headers: {},
  config: {},
} as AxiosResponse;

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

describe("VersionLockDialog", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("shows one row per user-facing version, collapsing multiple revisions of the same version", async () => {
    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock setup
    const apiGet = InvApiService.get;
    vi.mocked(apiGet).mockResolvedValue(revisionsResponse);
    renderDialog();
    expect(await screen.findByText("Version 1")).toBeInTheDocument();
    expect(await screen.findByText("Version 2")).toBeInTheDocument();
    // the raw Envers revision ids must NOT be presented as versions
    expect(screen.queryByText("Version 10")).not.toBeInTheDocument();
    expect(screen.queryByText("Version 11")).not.toBeInTheDocument();
    expect(screen.queryByText("Version 22")).not.toBeInTheDocument();
    expect(vi.mocked(apiGet)).toHaveBeenCalledWith("samples/42/revisions");
  });

  it("calls onConfirm with the chosen user-facing version, not the audit revisionId", async () => {
    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock setup
    const apiGet = InvApiService.get;
    vi.mocked(apiGet).mockResolvedValue(revisionsResponse);
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    renderDialog({ onConfirm });

    const row = await screen.findByText("Version 2");
    await user.click(row);
    await user.click(screen.getByRole("button", { name: /lock to selected/i }));

    expect(onConfirm).toHaveBeenCalledWith(2);
  });

  it("re-syncs the selection when reopened after an abandoned edit", async () => {
    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock setup
    const apiGet = InvApiService.get;
    vi.mocked(apiGet).mockResolvedValue(revisionsResponse);
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
    await user.click(await screen.findByText("Version 1"));
    rerender(
      <ThemeProvider theme={materialTheme}>
        <VersionLockDialog
          open={false}
          currentVersionPin={null}
          {...stableProps}
        />
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
    expect(await screen.findByText("Version 2")).toBeInTheDocument();
    const version2Radio = screen
      .getAllByRole("radio")
      .find((radio) => radio.getAttribute("value") === "2");
    expect(version2Radio).toBeChecked();
  });

  it("calls onConfirm with null when the user selects 'latest' after a pin was in place", async () => {
    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock setup
    const apiGet = InvApiService.get;
    vi.mocked(apiGet).mockResolvedValue(revisionsResponse);
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    renderDialog({ onConfirm, currentVersionPin: 1 });

    const latestRow = await screen.findByText(/^latest$/i);
    await user.click(latestRow);
    await user.click(screen.getByRole("button", { name: /lock to selected/i }));

    expect(onConfirm).toHaveBeenCalledWith(null);
  });
});

describe("VersionLockDialog (SD/ELN document target)", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("fetches SD revisions from the ELN endpoint and shows a row per version", async () => {
    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock setup
    const axiosGet = axios.get;
    vi.mocked(axiosGet).mockResolvedValue(elnRevisionsResponse);
    renderDialog({ globalId: "SD55" });

    expect(await screen.findByText("Version 1")).toBeInTheDocument();
    expect(await screen.findByText("Version 2")).toBeInTheDocument();
    expect(vi.mocked(axiosGet)).toHaveBeenCalledWith(
      "/workspace/revisionHistory/ajax/55/versions",
    );
  });

  it("pins to the document version number, not the audit revision id", async () => {
    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock setup
    const axiosGet = axios.get;
    vi.mocked(axiosGet).mockResolvedValue(elnRevisionsResponse);
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    renderDialog({ globalId: "SD55", onConfirm });

    await user.click(await screen.findByText("Version 2"));
    await user.click(screen.getByRole("button", { name: /lock to selected/i }));

    // version 2 maps to audit revision 202; the pin must be the version number (2).
    expect(onConfirm).toHaveBeenCalledWith(2);
  });

  it("still shows the cannot-resolve fallback for unsupported ELN types (NB)", () => {
    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock setup
    const axiosGet = axios.get;
    renderDialog({ globalId: "NB9" });

    expect(
      screen.getByText(/cannot resolve version history for NB9/i),
    ).toBeInTheDocument();
    expect(vi.mocked(axiosGet)).not.toHaveBeenCalled();
  });

  it("degrades to the latest-only view when the SD revisions fetch fails", async () => {
    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock setup
    const axiosGet = axios.get;
    vi.mocked(axiosGet).mockRejectedValue(new Error("network down"));
    renderDialog({ globalId: "SD55" });

    // SD is a supported target, so this is NOT the cannot-resolve fallback...
    expect(
      screen.queryByText(/cannot resolve version history/i),
    ).not.toBeInTheDocument();
    // ...the picker still renders with the Latest option and no version rows.
    expect(await screen.findByText(/^latest$/i)).toBeInTheDocument();
    expect(screen.queryByText("Version 1")).not.toBeInTheDocument();
  });
});
