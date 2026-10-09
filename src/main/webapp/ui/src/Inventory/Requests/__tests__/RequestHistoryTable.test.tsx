import { ThemeProvider } from "@mui/material/styles";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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
    createdBy: { id: 1, firstName: "Olive", lastName: "Owner" },
    reason: null,
    transferredSample: null,
    ...overrides,
  };
}

function renderTable(statusChanges: Array<ApiSampleRequestStatusChangeItem>) {
  return render(
    <ThemeProvider theme={materialTheme}>
      <RequestHistoryTable statusChanges={statusChanges} />
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

  test("shows the date column by default", () => {
    const created = "2026-01-01T10:00:00Z";
    renderTable([change({ created })]);

    // Computed via the same formatter under test, rather than a hardcoded literal, so this
    // doesn't depend on the test runner's own timezone.
    expect(screen.getByText(isoToLocale(created))).toBeInTheDocument();
  });

  test("switches to the Additional Notes column, showing each change's reason or a placeholder", async () => {
    const user = userEvent.setup();
    const created = "2026-01-01T10:00:00Z";
    renderTable([
      change({ id: 1, reason: "Sample no longer available", created }),
      change({ id: 2, reason: null, created }),
    ]);

    await user.click(screen.getByRole("button", { name: "inventory:tables.adjustableHeadCell.columnOptions" }));
    await user.click(
      screen.getByRole("menuitem", { name: "inventory:requestsManagement.detail.history.columns.additionalNotes" }),
    );

    expect(screen.getByText("Sample no longer available")).toBeInTheDocument();
    expect(screen.getByText("inventory:requestsManagement.detail.fields.noComment")).toBeInTheDocument();
    expect(screen.queryByText(isoToLocale(created))).not.toBeInTheDocument();
  });
});
