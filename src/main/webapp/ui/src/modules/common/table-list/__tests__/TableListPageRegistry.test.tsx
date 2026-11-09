import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StrictMode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { TableListPersistenceIdentity } from "../persistence";
import { TableList } from "../TableList";
import { createTableListPageRegistry, TableListPageRegistryProvider } from "../TableListPageRegistry";
import { config, emptyFilters, records } from "./fixtures/tableList";

function Table({ label, identity }: { label: string; identity?: TableListPersistenceIdentity }) {
  return (
    <section aria-label={label}>
      <TableList
        config={config}
        rows={records}
        getRowId={(row) => row.id}
        persistenceIdentity={identity}
        features={{
          filtering: { value: emptyFilters, onChange: () => undefined },
          sorting: false,
          pagination: false,
          columns: false,
        }}
      />
    </section>
  );
}

function renderTables(first?: TableListPersistenceIdentity, second?: TableListPersistenceIdentity) {
  return render(
    <TableListPageRegistryProvider>
      <Table label="First table" identity={first} />
      <Table label="Second table" identity={second} />
    </TableListPageRegistryProvider>,
  );
}

const identity = (parameterPrefix: string, storageKey: string) => ({ parameterPrefix, storageKey });

afterEach(() => vi.restoreAllMocks());

describe("several tables on one page", () => {
  it("gives each table's controls their own panel", async () => {
    const user = userEvent.setup();
    renderTables();
    const filters = (table: string) =>
      within(screen.getByRole("region", { name: table })).getByRole("button", {
        name: "common:tableList.filters.noneApplied",
      });

    await user.click(filters("First table"));
    await user.click(filters("Second table"));

    const first = filters("First table").getAttribute("aria-controls");
    const second = filters("Second table").getAttribute("aria-controls");
    expect(first).not.toBe(second);
    expect(
      within(screen.getByRole("region", { name: "First table" })).getByRole("region", {
        name: "common:tableList.filters.title",
      }),
    ).toHaveAttribute("id", first);
    expect(
      within(screen.getByRole("region", { name: "Second table" })).getByRole("region", {
        name: "common:tableList.filters.title",
      }),
    ).toHaveAttribute("id", second);
  });

  it.each([
    ["URL parameter prefix", identity("records", "a"), identity("records", "b"), 'parameterPrefix "records"'],
    [
      "storage key",
      identity("a", "rspace.tableList.records.view"),
      identity("b", "rspace.tableList.records.view"),
      "tableId",
    ],
  ])("reports two persisted tables that share a %s", (_label, first, second, named) => {
    const error = vi.spyOn(console, "error").mockImplementation(() => undefined);
    renderTables(first, second);

    expect(error).toHaveBeenCalledOnce();
    expect(error.mock.calls[0][0]).toContain(named);
  });

  it("accepts persisted tables with distinct options and tables without persistence", () => {
    const error = vi.spyOn(console, "error").mockImplementation(() => undefined);
    renderTables(identity("first", "first"), identity("second", "second"));
    renderTables();

    expect(error).not.toHaveBeenCalled();
  });

  it("keeps one registration when React detaches and reattaches the table root", () => {
    const error = vi.spyOn(console, "error").mockImplementation(() => undefined);
    const { unmount } = render(
      <StrictMode>
        <TableListPageRegistryProvider>
          <Table label="First table" identity={identity("records", "records")} />
        </TableListPageRegistryProvider>
      </StrictMode>,
    );
    unmount();

    expect(error).not.toHaveBeenCalled();
  });

  it("releases only the unmounted table's claims", () => {
    const error = vi.spyOn(console, "error").mockImplementation(() => undefined);
    const registry = createTableListPageRegistry();
    const claims = [{ kind: "parameterPrefix" as const, value: "records" }];
    registry.register("first", "records", claims);
    const releaseSecond = registry.register("second", "records", claims);
    expect(error).toHaveBeenCalledOnce();

    releaseSecond();
    registry.register("third", "records", claims);
    expect(error).toHaveBeenCalledTimes(2);
  });
});
