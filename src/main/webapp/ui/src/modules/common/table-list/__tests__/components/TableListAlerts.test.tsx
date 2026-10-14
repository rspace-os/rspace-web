import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import { TableList, type TableListAlert, useTableListAlerts } from "../../TableList";
import { config, emptyFilters, records, type TestRecord } from "../fixtures/tableListFixtures";

function RowActions({
  row,
  onRemove,
  onRestore,
  failUndo,
}: {
  row: TestRecord;
  onRemove: (id: string) => void;
  onRestore: (id: string) => void;
  failUndo: boolean;
}) {
  const alerts = useTableListAlerts();
  const removed = (id: string, title: string): TableListAlert => ({
    id: `removed-${id}`,
    message: `Removed ${title}.`,
    undo: {
      run: async () => {
        if (failUndo) throw new Error("slot taken");
        onRestore(id);
      },
      focusRowId: id,
      describeError: (error) => `Could not restore ${title}: ${(error as Error).message}`,
    },
  });
  return (
    <>
      <button
        type="button"
        onClick={() => {
          // The control that acted goes with its row, as after a real cancellation.
          onRemove(row.id);
          alerts.push(removed(row.id, row.title));
        }}
      >
        {`Remove ${row.title}`}
      </button>
      <button
        type="button"
        onClick={() => {
          // As after a server action: the alert first, the row only once its list has refetched.
          alerts.push(removed(row.id, row.title));
          window.setTimeout(() => onRemove(row.id), 50);
        }}
      >
        {`Remove ${row.title} after a refetch`}
      </button>
      <button
        type="button"
        onClick={() =>
          alerts.push(
            { id: "first", message: "First change." },
            { id: "second", message: "Second change.", tone: "success" },
          )
        }
      >
        {`Report two changes for ${row.title}`}
      </button>
    </>
  );
}

function Harness({ failUndo = false }: { failUndo?: boolean }) {
  const [removedIds, setRemovedIds] = useState<ReadonlySet<string>>(new Set());
  return (
    <TableList
      queryString={false}
      config={config}
      rows={records.filter(({ id }) => !removedIds.has(id))}
      getRowId={(row) => row.id}
      presentations={{ table: "all", cards: false }}
      features={{
        filtering: { value: emptyFilters, onChange: () => undefined },
        sorting: false,
        pagination: false,
        columns: false,
      }}
      rowActions={{
        id: "actions",
        label: "Actions",
        renderCell: ({ row }) => (
          <RowActions
            row={row}
            failUndo={failUndo}
            onRemove={(id) => setRemovedIds((current) => new Set(current).add(id))}
            onRestore={(id) =>
              setRemovedIds((current) => {
                const next = new Set(current);
                next.delete(id);
                return next;
              })
            }
          />
        ),
        renderInteraction: () => null,
      }}
    />
  );
}

function alertList() {
  return screen.getByRole("list", { name: "common:tableList.alerts.label" });
}

describe("TableList row actions", () => {
  it("keeps row-action cells mounted across refetches, even with an inline getRowId", async () => {
    const user = userEvent.setup();
    const rowActions = {
      id: "actions",
      label: "Actions",
      renderCell: ({ row }: { row: TestRecord }) => <input aria-label={`Note for ${row.title}`} />,
      renderInteraction: () => null,
    };
    const table = (rows: readonly TestRecord[]) => (
      <TableList
        queryString={false}
        config={config}
        rows={rows}
        getRowId={(row) => row.id}
        presentations={{ table: "all", cards: false }}
        features={{ filtering: false, sorting: false, pagination: false, columns: false }}
        rowActions={rowActions}
      />
    );
    const { rerender } = render(table(records));
    await user.type(screen.getByRole("textbox", { name: "Note for Alpha" }), "open menu");

    // A refetch hands the table a new rows array; a re-mounted cell would lose the input's state.
    rerender(table(records.map((record) => ({ ...record }))));

    expect(screen.getByRole("textbox", { name: "Note for Alpha" })).toHaveValue("open menu");
  });
});

describe("TableList alerts", () => {
  it("shows several alerts from one action, newest first, without taking focus from a control that remains", async () => {
    const user = userEvent.setup();
    render(<Harness />);

    const report = screen.getByRole("button", { name: "Report two changes for Alpha" });
    await user.click(report);

    const items = within(alertList()).getAllByRole("listitem");
    expect(items.map((item) => item.textContent)).toEqual([
      expect.stringContaining("Second change."),
      expect.stringContaining("First change."),
    ]);
    expect(report).toHaveFocus();
  });

  it("replaces an alert pushed again with the same id", async () => {
    const user = userEvent.setup();
    render(<Harness />);

    await user.click(screen.getByRole("button", { name: "Report two changes for Alpha" }));
    await user.click(screen.getByRole("button", { name: "Report two changes for Beta" }));

    expect(within(alertList()).getAllByRole("listitem")).toHaveLength(2);
  });

  it("focuses the alert when the acting row goes, then the next alert, then Filters as each is dismissed", async () => {
    const user = userEvent.setup();
    render(<Harness />);

    await user.click(screen.getByRole("button", { name: "Remove Alpha" }));
    const alpha = within(alertList()).getByRole("listitem", { name: "Removed Alpha." });
    await waitFor(() => expect(alpha).toHaveFocus());
    await user.click(screen.getByRole("button", { name: "Remove Beta" }));
    const beta = within(alertList()).getByRole("listitem", { name: "Removed Beta." });
    await waitFor(() => expect(beta).toHaveFocus());

    await user.click(within(beta).getByRole("button", { name: "common:tableList.alerts.dismiss" }));
    await waitFor(() => expect(within(alertList()).getByRole("listitem", { name: "Removed Alpha." })).toHaveFocus());

    await user.click(screen.getByRole("button", { name: "common:tableList.alerts.dismiss" }));
    expect(screen.queryByRole("list", { name: "common:tableList.alerts.label" })).not.toBeInTheDocument();
    await waitFor(() => expect(document.activeElement).toHaveAttribute("data-table-list-filters"));
  });

  it("moves focus to the alert when the acting row leaves only after the alert appears", async () => {
    const user = userEvent.setup();
    render(<Harness />);

    const remove = screen.getByRole("button", { name: "Remove Beta after a refetch" });
    await user.click(remove);
    const beta = within(alertList()).getByRole("listitem", { name: "Removed Beta." });
    expect(remove).toHaveFocus();

    await waitFor(() => expect(remove).not.toBeInTheDocument());
    await waitFor(() => expect(beta).toHaveFocus());
  });

  it("undoes the action, removes the alert and focuses the row it brings back", async () => {
    const user = userEvent.setup();
    render(<Harness />);

    await user.click(screen.getByRole("button", { name: "Remove Gamma" }));
    expect(screen.queryByRole("button", { name: "Remove Gamma" })).not.toBeInTheDocument();
    await user.click(within(alertList()).getByRole("button", { name: "common:tableList.alerts.undo" }));

    await waitFor(() => expect(screen.getByRole("button", { name: "Remove Gamma" })).toHaveFocus());
    expect(screen.queryByRole("list", { name: "common:tableList.alerts.label" })).not.toBeInTheDocument();
  });

  it("keeps a failed undo's alert with the reason and without another Undo", async () => {
    const user = userEvent.setup();
    render(<Harness failUndo />);

    await user.click(screen.getByRole("button", { name: "Remove Alpha" }));
    await user.click(within(alertList()).getByRole("button", { name: "common:tableList.alerts.undo" }));

    const failed = await within(alertList()).findByRole("listitem", { name: "Could not restore Alpha: slot taken" });
    await waitFor(() => expect(failed).toHaveFocus());
    expect(within(failed).queryByRole("button", { name: "common:tableList.alerts.undo" })).not.toBeInTheDocument();
    expect(within(failed).getByRole("button", { name: "common:tableList.alerts.dismiss" })).toBeEnabled();
    expect(screen.queryByRole("button", { name: "Remove Alpha" })).not.toBeInTheDocument();
  });
});
