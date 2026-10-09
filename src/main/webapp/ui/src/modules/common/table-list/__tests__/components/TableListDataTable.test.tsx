import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import type { CollectionConfig } from "@/modules/common/collection/collectionConfig";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import commonEnglish from "@/modules/common/i18n/locales/en-US/common.json";
import { TableList } from "../../TableList";
import { config, firstPage, records, type TestRecord } from "../fixtures/tableListFixtures";

describe("TableList data table", () => {
  it("keeps cards opt-in", () => {
    render(
      <TableList
        queryString={false}
        config={config}
        rows={[records[0]]}
        getRowId={(row) => row.id}
        features={{ filtering: false, sorting: false, pagination: false, columns: false }}
      />,
    );

    expect(screen.getByRole("table")).toBeVisible();
    expect(screen.queryByRole("region", { name: "common:tableList.cardView" })).not.toBeInTheDocument();
  });

  it("renders a standardized card-only presentation with full-width custom fields and footer actions", async () => {
    const { container } = render(
      <TableList
        queryString={false}
        config={config}
        rows={[records[0]]}
        getRowId={(row) => row.id}
        presentations={{ table: false, cards: "all" }}
        uiColumns={[
          {
            id: "summary",
            label: "Summary",
            card: { fullWidth: true },
            renderCell: (row) => <p>{`${row.owner} scored ${row.score}`}</p>,
          },
        ]}
        rowActions={{
          id: "actions",
          label: "Actions",
          renderCell: ({ row }) => <button type="button">{`Open ${row.title}`}</button>,
          renderInteraction: () => null,
        }}
        features={{ filtering: false, sorting: false, pagination: false, columns: false }}
      />,
    );

    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    const cardView = screen.getByRole("region", { name: "common:tableList.cardView" });
    const card = within(cardView).getByRole("article", { name: "Alpha" });
    expect(within(card).getByText("Ada scored 8").closest("dd")?.parentElement).toHaveClass("col-span-full");
    expect(within(card).getByRole("button", { name: "Open Alpha" }).closest("footer")).toBeInTheDocument();
    await expectAccessible(container);
  });

  it("uses the same filtered, sorted, and paginated row model for cards", () => {
    render(
      <TableList
        queryString={false}
        config={config}
        rows={records}
        getRowId={(row) => row.id}
        clientSide
        presentations={{ table: false, cards: "all" }}
        features={{
          filtering: {
            value: {
              search: "ada",
              expression: { kind: "comparison", field: "enabled", operator: "equals", value: true },
            },
            onChange: vi.fn(),
          },
          sorting: { value: [{ field: "modifiedAt", direction: "desc" }], onChange: vi.fn() },
          pagination: { value: { pageIndex: 0, pageSize: 1 }, rowCount: records.length, onChange: vi.fn() },
          columns: false,
        }}
      />,
    );

    expect(screen.getByRole("article", { name: "Gamma" })).toBeVisible();
    expect(screen.queryByRole("article", { name: "Alpha" })).not.toBeInTheDocument();
    expect(screen.queryByRole("article", { name: "Beta" })).not.toBeInTheDocument();
  });

  it("supplies the filtered, sorted, and paginated row model to a custom presentation", () => {
    render(
      <TableList
        queryString={false}
        config={config}
        rows={records}
        getRowId={(row) => row.id}
        clientSide
        renderRows={(visibleRows) => (
          <ol aria-label="Calendar rows">
            {visibleRows.map((row) => (
              <li key={row.id}>{row.title}</li>
            ))}
          </ol>
        )}
        features={{
          filtering: {
            value: {
              search: "ada",
              expression: { kind: "comparison", field: "enabled", operator: "equals", value: true },
            },
            onChange: vi.fn(),
          },
          sorting: { value: [{ field: "modifiedAt", direction: "desc" }], onChange: vi.fn() },
          pagination: { value: { pageIndex: 0, pageSize: 1 }, rowCount: records.length, onChange: vi.fn() },
          columns: false,
        }}
      />,
    );

    const calendar = screen.getByRole("list", { name: "Calendar rows" });
    expect(within(calendar).getByRole("listitem")).toHaveTextContent("Gamma");
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("supports row and current-page selection from cards", async () => {
    const user = userEvent.setup();

    function Harness() {
      const [selectedRowIds, setSelectedRowIds] = useState<ReadonlySet<string>>(new Set(["3"]));
      return (
        <TableList
          queryString={false}
          config={config}
          rows={records.slice(0, 2)}
          getRowId={(row) => row.id}
          presentations={{ table: false, cards: "all" }}
          features={{ filtering: false, sorting: false, pagination: false, columns: false }}
          selection={{
            value: selectedRowIds,
            onChange: setSelectedRowIds,
            maximumCount: 1000,
            getRowLabel: (row) => row.title,
            renderActions: ({ selectedRowIds: ids }) => <span>{Array.from(ids).sort().join(",")}</span>,
          }}
        />
      );
    }

    render(<Harness />);
    await user.click(within(screen.getByRole("article", { name: "Alpha" })).getByRole("checkbox"));
    expect(screen.getByText("1,3")).toBeVisible();

    await user.click(screen.getByRole("checkbox", { name: "common:tableList.selection.selectAllPage" }));
    expect(screen.getByText("1,2,3")).toBeVisible();
  });

  it("uses the shared rows-per-page options by default", () => {
    render(
      <TableList
        queryString={false}
        config={{ ...config, pagination: undefined }}
        rows={records}
        getRowId={(row) => row.id}
        features={{
          filtering: false,
          sorting: false,
          pagination: { value: { pageIndex: 0, pageSize: 20 }, rowCount: records.length, onChange: vi.fn() },
          columns: false,
        }}
      />,
    );

    expect(
      within(screen.getByRole("combobox", { name: "common:tableList.rowsPerPage" }))
        .getAllByRole("option")
        .map((option) => option.textContent),
    ).toEqual(["10", "20", "30", "40", "50"]);
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  });

  it("reports controlled pagination intent", async () => {
    const user = userEvent.setup();
    const onPageChange = vi.fn();
    render(
      <TableList
        queryString={false}
        config={config}
        rows={records.slice(0, 2)}
        getRowId={(row) => row.id}
        features={{
          filtering: false,
          sorting: false,
          pagination: { value: firstPage, rowCount: records.length, onChange: onPageChange },
          columns: false,
        }}
      />,
    );

    await user.click(screen.getByRole("button", { name: "common:tableList.actions.nextPage" }));
    expect(onPageChange).toHaveBeenCalledWith({ pageIndex: 1, pageSize: 2 });
  });

  it("reflects sorting through aria-sort", async () => {
    const user = userEvent.setup();
    function Harness() {
      const [sorting, setSorting] = useState<readonly { field: keyof TestRecord; direction: "asc" | "desc" }[]>([]);
      return (
        <TableList
          queryString={false}
          config={config}
          rows={records}
          getRowId={(row) => row.id}
          features={{
            filtering: false,
            sorting: { value: sorting, onChange: setSorting },
            pagination: false,
            columns: false,
          }}
        />
      );
    }
    render(<Harness />);

    const titleHeader = screen.getByRole("columnheader", { name: /common:tableList.examples.fields.title/ });
    expect(titleHeader).toHaveAttribute("aria-sort", "none");
    await user.click(screen.getAllByRole("button", { name: "common:tableList.actions.sortBy" })[0]);
    expect(titleHeader).toHaveAttribute("aria-sort", "ascending");
  });

  it("processes local rows through the table row models", () => {
    render(
      <TableList
        queryString={false}
        config={config}
        rows={records}
        getRowId={(row) => row.id}
        clientSide
        features={{
          filtering: {
            value: {
              search: "ada",
              expression: { kind: "comparison", field: "enabled", operator: "equals", value: true },
            },
            onChange: vi.fn(),
          },
          sorting: {
            value: [{ field: "modifiedAt", direction: "desc" }],
            onChange: vi.fn(),
          },
          pagination: { value: { pageIndex: 0, pageSize: 1 }, rowCount: records.length, onChange: vi.fn() },
          columns: false,
        }}
      />,
    );

    expect(screen.getByRole("cell", { name: "Gamma" })).toBeVisible();
    expect(screen.queryByRole("cell", { name: "Alpha" })).not.toBeInTheDocument();
    expect(screen.queryByRole("cell", { name: "Beta" })).not.toBeInTheDocument();
  });

  it("does not rebuild remote rows for query state alone", async () => {
    const user = userEvent.setup();
    const renderCell = vi.fn((row: TestRecord) => row.title);
    const uiColumns = [{ id: "copy", label: "Copy", renderCell }] as const;

    function Harness() {
      const [sorting, setSorting] = useState<readonly { field: keyof TestRecord; direction: "asc" | "desc" }[]>([]);
      return (
        <>
          <button type="button" onClick={() => setSorting([{ field: "title", direction: "asc" }])}>
            {"Change remote sorting"}
          </button>
          <TableList
            queryString={false}
            config={config}
            rows={records}
            getRowId={(row) => row.id}
            uiColumns={uiColumns}
            features={{
              filtering: false,
              sorting: { value: sorting, onChange: setSorting },
              pagination: false,
              columns: false,
            }}
          />
        </>
      );
    }

    render(<Harness />);
    expect(renderCell).toHaveBeenCalledTimes(records.length);

    await user.click(screen.getByRole("button", { name: "Change remote sorting" }));
    expect(renderCell).toHaveBeenCalledTimes(records.length);
  });

  it("updates remote cells when visible columns change without new rows", async () => {
    const user = userEvent.setup();

    function Harness() {
      const [columns, setColumns] = useState(config.defaultColumns);
      return (
        <TableList
          queryString={false}
          config={config}
          rows={records}
          getRowId={(row) => row.id}
          features={{
            filtering: false,
            sorting: false,
            pagination: false,
            columns: { value: columns, onChange: setColumns },
          }}
        />
      );
    }

    render(<Harness />);
    expect(screen.getByRole("cell", { name: "Grace" })).toBeVisible();

    await user.click(screen.getByRole("button", { name: "common:tableList.toolbar.columns" }));
    await user.click(screen.getAllByRole("button", { name: "common:tableList.actions.hideColumn" })[1]);

    expect(screen.queryByRole("cell", { name: "Grace" })).not.toBeInTheDocument();
    expect(screen.getByRole("cell", { name: "Beta" })).toBeVisible();
  });

  it("searches target fields in a client-side to-many relationship", () => {
    type RelatedRecord = {
      id: string;
      title: string;
      targets: { relationTo: "people"; value: readonly { name: string }[] };
    };
    const relationshipConfig = resolveCollectionConfig<RelatedRecord>({
      slug: "relatedRecords",
      idField: "id",
      labels: { singularKey: "record", pluralKey: "records" },
      useAsTitle: "title",
      defaultColumns: ["title"],
      listSearchableFields: ["targets.name"],
      fields: [
        { name: "id", type: "text", labelKey: "id", list: false },
        { name: "title", type: "text", labelKey: "title" },
        {
          name: "targets",
          type: "relationship",
          relationTo: "people",
          hasMany: true,
          labelKey: "targets",
          list: false,
        },
      ],
    } satisfies CollectionConfig<RelatedRecord>);
    const relatedRows: readonly RelatedRecord[] = [
      { id: "1", title: "Matching", targets: { relationTo: "people", value: [{ name: "Ada" }] } },
      { id: "2", title: "Other", targets: { relationTo: "people", value: [{ name: "Grace" }] } },
    ];

    render(
      <TableList<RelatedRecord>
        queryString={false}
        config={relationshipConfig}
        rows={relatedRows}
        getRowId={(row) => row.id}
        clientSide
        features={{
          filtering: { value: { search: "ada", expression: null }, onChange: vi.fn() },
          sorting: false,
          pagination: false,
          columns: false,
        }}
      />,
    );

    expect(screen.getByRole("cell", { name: "Matching" })).toBeVisible();
    expect(screen.queryByRole("cell", { name: "Other" })).not.toBeInTheDocument();
  });

  it("renders presentation-only columns without document fields", () => {
    render(
      <TableList
        queryString={false}
        config={config}
        rows={[records[0]]}
        getRowId={(row) => row.id}
        uiColumns={[
          {
            id: "actions",
            label: "Actions",
            renderCell: (row) => <button type="button" aria-label={`Edit ${row.title}`} />,
          },
        ]}
        features={{ filtering: false, sorting: false, pagination: false, columns: false }}
      />,
    );

    expect(screen.getByRole("columnheader", { name: "Actions" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Edit Alpha" })).toBeVisible();
  });

  it("selects and clears only the visible rows while retaining unloaded IDs", async () => {
    const user = userEvent.setup();

    function Harness() {
      const [selectedRowIds, setSelectedRowIds] = useState<ReadonlySet<string>>(new Set(["3"]));
      return (
        <TableList
          queryString={false}
          config={config}
          rows={records.slice(0, 2)}
          getRowId={(row) => row.id}
          features={{ filtering: false, sorting: false, pagination: false, columns: false }}
          selection={{
            value: selectedRowIds,
            onChange: setSelectedRowIds,
            maximumCount: 1000,
            getRowLabel: (row) => row.title,
            renderActions: ({ selectedRowIds: actionIds }) => <span>{Array.from(actionIds).sort().join(",")}</span>,
          }}
        />
      );
    }

    render(<Harness />);
    const table = screen.getByRole("table");
    const headerCheckbox = within(table).getAllByRole("checkbox")[0];
    const alphaCheckbox = within(screen.getByRole("row", { name: /Alpha/ })).getByRole("checkbox");

    await user.click(alphaCheckbox);
    expect(headerCheckbox).toHaveAttribute("aria-checked", "mixed");

    await user.click(headerCheckbox);
    expect(screen.getByText("1,2,3")).toBeVisible();
    expect(headerCheckbox).toBeChecked();

    await user.click(headerCheckbox);
    expect(screen.getByText("3")).toBeVisible();
    expect(headerCheckbox).not.toBeChecked();
  });

  it("keeps selection across remote pages and restores selected row states", async () => {
    const user = userEvent.setup();

    function Harness() {
      const [selectedRowIds, setSelectedRowIds] = useState<ReadonlySet<string>>(new Set());
      const [page, setPage] = useState(firstPage);
      const pageRows = page.pageIndex === 0 ? records.slice(0, 2) : records.slice(2);
      return (
        <TableList
          queryString={false}
          config={config}
          rows={pageRows}
          getRowId={(row) => row.id}
          features={{
            filtering: false,
            sorting: false,
            pagination: { value: page, rowCount: records.length, onChange: setPage },
            columns: false,
          }}
          selection={{
            value: selectedRowIds,
            onChange: setSelectedRowIds,
            maximumCount: 1000,
            renderActions: ({ selectedRowIds: actionIds }) => <span>{`${actionIds.size} selected IDs`}</span>,
          }}
        />
      );
    }

    render(<Harness />);
    await user.click(within(screen.getByRole("row", { name: /Alpha/ })).getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.nextPage" }));
    await user.click(within(screen.getByRole("row", { name: /Gamma/ })).getByRole("checkbox"));
    expect(screen.getByText("2 selected IDs")).toBeVisible();

    await user.click(screen.getByRole("button", { name: "common:tableList.actions.previousPage" }));
    expect(within(screen.getByRole("row", { name: /Alpha/ })).getByRole("checkbox")).toBeChecked();
    expect(within(screen.getByRole("row", { name: /Beta/ })).getByRole("checkbox")).not.toBeChecked();
  });

  it("shows singular and plural selected-row counts and clears the full selection", async () => {
    const user = userEvent.setup();

    function Harness() {
      const [selectedRowIds, setSelectedRowIds] = useState<ReadonlySet<string>>(new Set(["1"]));
      return (
        <TableList
          queryString={false}
          config={config}
          rows={records.slice(0, 2)}
          getRowId={(row) => row.id}
          features={{ filtering: false, sorting: false, pagination: false, columns: false }}
          selection={{
            value: selectedRowIds,
            onChange: setSelectedRowIds,
            maximumCount: 1000,
            renderActions: () => null,
          }}
        />
      );
    }

    await renderWithRealI18n(<Harness />, {
      resources: { common: commonEnglish },
      defaultNS: "common",
    });
    expect(screen.getByText("1 row selected")).toBeVisible();

    await user.click(within(screen.getByRole("row", { name: /Beta/ })).getByRole("checkbox"));
    expect(screen.getByText("2 rows selected")).toBeVisible();

    await user.click(screen.getByRole("button", { name: "Clear selection" }));
    expect(screen.queryByRole("region", { name: "Selected rows actions" })).not.toBeInTheDocument();
  });

  it("enforces the selection limit while keeping selected rows removable", async () => {
    const user = userEvent.setup();
    const selectedOffPage = Array.from({ length: 999 }, (_, index) => `off-page-${index}`);

    function Harness() {
      const [selectedRowIds, setSelectedRowIds] = useState<ReadonlySet<string>>(new Set(selectedOffPage));
      return (
        <TableList
          queryString={false}
          config={config}
          rows={records.slice(0, 2)}
          getRowId={(row) => row.id}
          features={{ filtering: false, sorting: false, pagination: false, columns: false }}
          selection={{
            value: selectedRowIds,
            onChange: setSelectedRowIds,
            maximumCount: 1000,
            renderActions: () => null,
          }}
        />
      );
    }

    await renderWithRealI18n(<Harness />, {
      resources: { common: commonEnglish },
      defaultNS: "common",
    });
    const table = screen.getByRole("table");
    const headerCheckbox = within(table).getAllByRole("checkbox")[0];
    const alphaCheckbox = within(screen.getByRole("row", { name: /Alpha/ })).getByRole("checkbox");
    const betaCheckbox = within(screen.getByRole("row", { name: /Beta/ })).getByRole("checkbox");
    expect(headerCheckbox).toHaveAttribute("aria-disabled", "true");

    await user.click(alphaCheckbox);
    expect(alphaCheckbox).toBeChecked();
    expect(alphaCheckbox).not.toHaveAttribute("aria-disabled", "true");
    expect(betaCheckbox).toHaveAttribute("aria-disabled", "true");
    expect(screen.getByText("You can select up to 1,000 rows.")).toBeVisible();

    await user.click(alphaCheckbox);
    expect(alphaCheckbox).not.toBeChecked();
    expect(betaCheckbox).not.toHaveAttribute("aria-disabled", "true");
  });

  it("prevents selection changes while selection is disabled", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <TableList
        queryString={false}
        config={config}
        rows={records.slice(0, 2)}
        getRowId={(row) => row.id}
        features={{ filtering: false, sorting: false, pagination: false, columns: false }}
        selection={{
          value: new Set(["1"]),
          onChange,
          disabled: true,
          maximumCount: 1000,
          renderActions: () => null,
        }}
      />,
    );

    const table = screen.getByRole("table");
    within(table)
      .getAllByRole("checkbox")
      .forEach((checkbox) => {
        expect(checkbox).toHaveAttribute("aria-disabled", "true");
      });
    const clear = screen.getByRole("button", { name: "common:tableList.selection.clear" });
    expect(clear).toBeDisabled();
    await user.click(clear);
    expect(onChange).not.toHaveBeenCalled();
  });
});
