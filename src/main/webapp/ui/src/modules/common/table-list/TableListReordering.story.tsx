import { NuqsAdapter } from "nuqs/adapters/react";
import { useState } from "react";
import type { FieldName, SortRule } from "@/modules/common/collection/collectionConfig";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { TableList } from "./TableList";
import type { FilterExpression, FilterState } from "./tableListState";

type ReorderingRecord = {
  id: string;
  title: string;
  owner: string;
  score: number;
  enabled: boolean;
  modifiedAt: string;
};

const config = resolveCollectionConfig<ReorderingRecord>({
  slug: "reordering-records",
  idField: "id",
  labels: {
    singularKey: "tableList.examples.record",
    pluralKey: "tableList.examples.records",
  },
  useAsTitle: "title",
  defaultColumns: ["title", "owner", "score"],
  defaultSort: [
    { field: "title", direction: "asc" },
    { field: "owner", direction: "asc" },
    { field: "score", direction: "desc" },
  ],
  listSearchableFields: ["title", "owner"],
  fields: [
    { name: "id", labelKey: "tableList.examples.fields.id", type: "text", list: false },
    {
      name: "title",
      labelKey: "tableList.examples.fields.title",
      type: "text",
      list: { width: 280, minWidth: 180 },
    },
    {
      name: "owner",
      labelKey: "tableList.examples.fields.owner",
      type: "text",
      list: { width: 220, minWidth: 120 },
    },
    {
      name: "score",
      labelKey: "tableList.examples.fields.score",
      type: "number",
      list: { width: 160, minWidth: 80 },
    },
    { name: "enabled", labelKey: "tableList.examples.fields.enabled", type: "boolean" },
    { name: "modifiedAt", labelKey: "tableList.examples.fields.modified", type: "dateTime" },
  ],
});

const rows: readonly ReorderingRecord[] = [
  { id: "1", title: "Alpha", owner: "Ada", score: 8, enabled: true, modifiedAt: "2026-08-01" },
  { id: "2", title: "Beta", owner: "Grace", score: 4, enabled: false, modifiedAt: "2026-08-02" },
];

export const reorderingStorageKey = "rspace.tableList.reordering-browser.view";
export const reorderingWhere = Array.from({ length: 12 }, (_, index) => `title==rule-${index + 1}`).join(";");

const initialExpression: FilterExpression<ReorderingRecord> = {
  kind: "and",
  children: Array.from({ length: 12 }, (_, index) => ({
    kind: "comparison" as const,
    field: "title",
    operator: "equals" as const,
    value: `rule-${index + 1}`,
  })),
};
const resetChangeCountersLabel = "Reset change counters";

function filterOrder(expression: FilterExpression<ReorderingRecord> | null): string {
  if (!expression) return "";
  const rules = expression.kind === "and" ? expression.children : [expression];
  return rules.flatMap((rule) => (rule.kind === "comparison" ? [String(rule.value)] : [])).join(",");
}

function Demo({ persist }: { persist: boolean }) {
  const [filters, setFilters] = useState<FilterState<ReorderingRecord>>({
    search: "",
    expression: initialExpression,
  });
  const [sorting, setSorting] = useState<readonly SortRule<ReorderingRecord>[]>(config.defaultSort ?? []);
  const [columns, setColumns] = useState<readonly FieldName<ReorderingRecord>[]>(config.defaultColumns);
  const [commits, setCommits] = useState({ filters: 0, sorting: 0, columns: 0 });
  const commitCounts = `${commits.filters},${commits.sorting},${commits.columns}`;

  return (
    <div className="mx-auto w-180 max-w-full p-4">
      <TableList
        config={config}
        rows={rows}
        getRowId={(row) => row.id}
        clientSide
        reserveEmptyRows={false}
        uiColumns={[
          {
            id: "actions",
            label: "Actions",
            width: 120,
            minWidth: 96,
            renderCell: (row) => <button type="button" aria-label={`View ${row.title}`} />,
          },
        ]}
        queryString={persist ? { parameterPrefix: "reordering-browser", tableId: "reordering-browser" } : false}
        features={{
          filtering: {
            value: filters,
            onChange: (value) => {
              setFilters(value);
              setCommits((current) => ({ ...current, filters: current.filters + 1 }));
            },
          },
          sorting: {
            value: sorting,
            onChange: (value) => {
              setSorting(value);
              setCommits((current) => ({ ...current, sorting: current.sorting + 1 }));
            },
          },
          pagination: false,
          columns: {
            value: columns,
            onChange: (value) => {
              setColumns(value);
              setCommits((current) => ({ ...current, columns: current.columns + 1 }));
            },
          },
        }}
      />
      <div className="mt-4 grid gap-1 text-xs">
        <output aria-label="Filter state">{filterOrder(filters.expression)}</output>
        <output aria-label="Sorting state">{sorting.map((rule) => `${rule.field}:${rule.direction}`).join(",")}</output>
        <output aria-label="Column state">{columns.join(",")}</output>
        <output aria-label="Commit counts">{commitCounts}</output>
        <button type="button" onClick={() => setCommits({ filters: 0, sorting: 0, columns: 0 })}>
          {resetChangeCountersLabel}
        </button>
      </div>
    </div>
  );
}

export function TableListReorderingStory({ persist = false }: { persist?: boolean }) {
  return (
    <NuqsAdapter>
      <Demo persist={persist} />
    </NuqsAdapter>
  );
}
