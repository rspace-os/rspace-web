import { ThemeProvider } from "@mui/material/styles";
import { render, screen, within } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import materialTheme from "@/theme";
import { isoToLocale } from "@/util/Util";
import RequestHistoryTable, { type ApiSampleRequestStatusChangeItem } from "../RequestHistoryTable";

function change(overrides: Partial<ApiSampleRequestStatusChangeItem> = {}): ApiSampleRequestStatusChangeItem {
  return {
    id: 1,
    status: "PENDING",
    created: "2026-01-01T10:00:00Z",
    createdBy: { id: 1, username: "owner", firstName: "Olive", lastName: "Owner" },
    reason: null,
    transferredSample: null,
    ...overrides,
  };
}

function renderTable(statusChanges: Array<ApiSampleRequestStatusChangeItem>, requesterNote: string | null = null) {
  return render(
    <ThemeProvider theme={materialTheme}>
      <RequestHistoryTable statusChanges={statusChanges} requesterNote={requesterNote} />
    </ThemeProvider>,
  );
}

describe("RequestHistoryTable", () => {
  test("has no axe violations", async () => {
    const { container } = renderTable([change()]);
    await expectAccessible(container);
  });

  test("sorts status changes newest-first regardless of input order", () => {
    renderTable([
      change({ id: 1, status: "PENDING", created: "2026-01-01T10:00:00Z" }),
      change({ id: 2, status: "APPROVED", created: "2026-01-03T10:00:00Z" }),
      change({ id: 3, status: "FULFILLED", created: "2026-01-02T10:00:00Z" }),
    ]);

    const rows = screen.getAllByRole("row").slice(1); // drop the header row
    const statusTexts = rows.map((row) => within(row).getByText(/^inventory:requestsManagement\.status\./).textContent);
    expect(statusTexts).toEqual([
      "inventory:requestsManagement.status.approved",
      "inventory:requestsManagement.status.fulfilled",
      "inventory:requestsManagement.status.pending",
    ]);
  });

  test("shows the Date and Additional Notes columns together, every time", () => {
    const created = "2026-01-01T10:00:00Z";
    renderTable([
      change({ id: 1, status: "APPROVED", reason: "Sample no longer available", created }),
      change({ id: 2, status: "REJECTED", reason: null, created }),
    ]);

    // Computed via the same formatter under test, rather than a hardcoded literal, so this
    // doesn't depend on the test runner's own timezone.
    expect(screen.getAllByText(isoToLocale(created))).toHaveLength(2);
    expect(screen.getByText("Sample no longer available")).toBeInTheDocument();
    expect(screen.getByText("inventory:requestsManagement.detail.fields.noComment")).toBeInTheDocument();
  });

  test("shows the requester's own note, not a reason, in the PENDING row", () => {
    renderTable(
      [
        change({ id: 1, status: "PENDING", reason: null }),
        change({ id: 2, status: "APPROVED", reason: "Looks good", created: "2026-01-02T10:00:00Z" }),
      ],
      "Need this for an upcoming experiment",
    );

    expect(screen.getByText("Need this for an upcoming experiment")).toBeInTheDocument();
    expect(screen.getByText("Looks good")).toBeInTheDocument();
  });

  test("falls back to the placeholder in the PENDING row when the requester left no note", () => {
    renderTable([change({ id: 1, status: "PENDING", reason: "A reason that should not be shown" })], null);

    expect(screen.getByText("inventory:requestsManagement.detail.fields.noComment")).toBeInTheDocument();
    expect(screen.queryByText("A reason that should not be shown")).not.toBeInTheDocument();
  });

  test("has no column-options cog", () => {
    renderTable([change()]);

    expect(
      screen.queryByRole("button", { name: "inventory:tables.adjustableHeadCell.columnOptions" }),
    ).not.toBeInTheDocument();
  });
});
