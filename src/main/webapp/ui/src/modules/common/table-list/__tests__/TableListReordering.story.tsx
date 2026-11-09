import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createMemoryHistory,
  createRootRoute,
  createRouter,
  RouterProvider,
  useLocation,
} from "@tanstack/react-router";
import { useState } from "react";
import { MemoryHistoryNuqsAdapter } from "@/__tests__/MemoryHistoryNuqsAdapter";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { type UseTableListResult, usePersistedTableList, useTableList } from "../hooks/useTableList";
import { tableViewDescriptor, tableViewStorageKey } from "../persistence";
import { TableList } from "../TableList";
import { tableViewRouteOptions } from "../tableViewRoute";
import type { FilterExpression } from "../types";
import "./i18n/tableListExamples";

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
    singularKey: "tableListExamples:record",
    pluralKey: "tableListExamples:records",
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
    { name: "id", labelKey: "tableListExamples:fields.id", type: "text", list: false },
    {
      name: "title",
      labelKey: "tableListExamples:fields.title",
      type: "text",
      list: { width: 280, minWidth: 180 },
    },
    {
      name: "owner",
      labelKey: "tableListExamples:fields.owner",
      type: "text",
      list: { width: 220, minWidth: 120 },
    },
    {
      name: "score",
      labelKey: "tableListExamples:fields.score",
      type: "number",
      list: { width: 160, minWidth: 80 },
    },
    { name: "enabled", labelKey: "tableListExamples:fields.enabled", type: "boolean" },
    { name: "modifiedAt", labelKey: "tableListExamples:fields.modified", type: "dateTime" },
  ],
});

const rows: readonly ReorderingRecord[] = [
  { id: "1", title: "Alpha", owner: "Ada", score: 8, enabled: true, modifiedAt: "2026-08-01" },
  { id: "2", title: "Beta", owner: "Grace", score: 4, enabled: false, modifiedAt: "2026-08-02" },
];

const reorderingTable = tableViewDescriptor(config, "discard", {
  parameterPrefix: "reordering-browser",
  tableId: "reordering-browser",
});
export const reorderingStorageKey = tableViewStorageKey(reorderingTable);
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

const tableOptions = {
  config,
  dataSource: { type: "client" as const, rows },
  features: { pagination: false },
  initialState: { filters: { search: "", expression: initialExpression } },
};

function Demo({ table }: { table: UseTableListResult<ReorderingRecord> }) {
  const [commits, setCommits] = useState({ filters: 0, sorting: 0, columns: 0 });
  const commitCounts = `${commits.filters},${commits.sorting},${commits.columns}`;
  const counted = <T,>(feature: false | { value: T; onChange: (value: T) => void }, key: keyof typeof commits) =>
    feature === false
      ? false
      : {
          ...feature,
          onChange: (value: T) => {
            feature.onChange(value);
            setCommits((current) => ({ ...current, [key]: current[key] + 1 }));
          },
        };
  const { features } = table.tableProps;
  const { filters, sorting, visibleFields: columns } = table.state;

  return (
    <div className="mx-auto w-180 max-w-full p-4">
      <TableList
        {...table.tableProps}
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
        features={{
          ...features,
          filtering: counted(features.filtering, "filters"),
          sorting: counted(features.sorting, "sorting"),
          columns: counted(features.columns, "columns"),
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

function LocalDemo() {
  return <Demo table={useTableList(tableOptions)} />;
}

function PersistedDemo() {
  const searchParameters = useLocation({ select: (location) => location.searchStr });
  return (
    <>
      <Demo table={usePersistedTableList({ ...tableOptions, persistence: reorderingTable })} />
      <output aria-label="Search parameters">{searchParameters}</output>
    </>
  );
}

/** With `persist`, the table runs on a memory router at `search`, whose route saves the view to storage. */
export function TableListReorderingStory({ persist = false, search = "" }: { persist?: boolean; search?: string }) {
  const [router] = useState(() =>
    createRouter({
      routeTree: createRootRoute({
        ...tableViewRouteOptions([reorderingTable]),
        component: () => (
          <MemoryHistoryNuqsAdapter>
            <PersistedDemo />
          </MemoryHistoryNuqsAdapter>
        ),
      }),
      history: createMemoryHistory({ initialEntries: [`/${search}`] }),
    }),
  );
  const [client] = useState(() => new QueryClient());
  return (
    <QueryClientProvider client={client}>
      {persist ? <RouterProvider router={router} /> : <LocalDemo />}
    </QueryClientProvider>
  );
}
