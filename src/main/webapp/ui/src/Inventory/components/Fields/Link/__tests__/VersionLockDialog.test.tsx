import { afterEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../../theme";
import InvApiService from "../../../../../common/InvApiService";
import { type AxiosResponse } from "axios";

vi.mock("../../../../../common/InvApiService", () => ({
  default: {
    get: vi.fn(),
  },
}));

import VersionLockDialog from "../VersionLockDialog";

const revisionsResponse = {
  data: {
    revisions: [
      {
        revisionId: 11,
        revisionType: "MOD",
        record: {
          id: 42,
          globalId: "SA42",
          name: "Sample foo",
          lastModified: "2026-01-15T10:00:00Z",
        },
      },
      {
        revisionId: 22,
        revisionType: "MOD",
        record: {
          id: 42,
          globalId: "SA42",
          name: "Sample foo v2",
          lastModified: "2026-02-15T10:00:00Z",
        },
      },
    ],
    revisionsCount: 2,
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

  it("fetches revisions from /samples/{id}/revisions and shows a row per revision", async () => {
    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock setup
    const apiGet = InvApiService.get;
    vi.mocked(apiGet).mockResolvedValue(revisionsResponse);
    renderDialog();
    expect(await screen.findByText(/version 11/i)).toBeInTheDocument();
    expect(await screen.findByText(/version 22/i)).toBeInTheDocument();
    expect(vi.mocked(apiGet)).toHaveBeenCalledWith("samples/42/revisions");
  });

  it("calls onConfirm with the chosen revisionId after the user selects a version", async () => {
    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock setup
    const apiGet = InvApiService.get;
    vi.mocked(apiGet).mockResolvedValue(revisionsResponse);
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    renderDialog({ onConfirm });

    const row = await screen.findByText(/version 22/i);
    await user.click(row);
    await user.click(screen.getByRole("button", { name: /lock to selected/i }));

    expect(onConfirm).toHaveBeenCalledWith(22);
  });

  it("calls onConfirm with null when the user selects 'latest' after a pin was in place", async () => {
    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock setup
    const apiGet = InvApiService.get;
    vi.mocked(apiGet).mockResolvedValue(revisionsResponse);
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    renderDialog({ onConfirm, currentVersionPin: 11 });

    const latestRow = await screen.findByText(/^latest$/i);
    await user.click(latestRow);
    await user.click(screen.getByRole("button", { name: /lock to selected/i }));

    expect(onConfirm).toHaveBeenCalledWith(null);
  });
});
