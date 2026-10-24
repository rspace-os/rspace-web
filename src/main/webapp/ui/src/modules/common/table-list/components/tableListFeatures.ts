import {
  columnFilteringFeature,
  columnOrderingFeature,
  columnResizingFeature,
  columnSizingFeature,
  columnVisibilityFeature,
  createFilteredRowModel,
  createPaginatedRowModel,
  createSortedRowModel,
  globalFilteringFeature,
  rowPaginationFeature,
  rowSortingFeature,
  tableFeatures,
} from "@tanstack/react-table";
import type { ReactNode } from "react";
import { formatList } from "@/modules/common/i18n/listFormat";
import type { TableListCardFieldLayout } from "../tableListState";

export type ColumnMeta = {
  labelKey: string;
  label?: string;
  cardLabel?: ReactNode;
  card?: TableListCardFieldLayout;
  descriptionKey?: string;
  description?: string;
};

export const selectionColumnId = "table-list-selection";

export const tableListFeatures = tableFeatures({
  columnMeta: {} as ColumnMeta,
  columnFilteringFeature,
  columnOrderingFeature,
  columnSizingFeature,
  columnResizingFeature,
  columnVisibilityFeature,
  globalFilteringFeature,
  rowSortingFeature,
  rowPaginationFeature,
  filteredRowModel: createFilteredRowModel(),
  sortedRowModel: createSortedRowModel(),
  paginatedRowModel: createPaginatedRowModel(),
});

export function displayValue(value: unknown, language: string | undefined): string {
  if (value === null || value === undefined) return "";
  if (value instanceof Date) return value.toLocaleString();
  if (Array.isArray(value))
    return formatList(
      value.map((item) => displayValue(item, language)),
      language,
      { type: "unit", style: "short" },
    );
  if (typeof value === "object") {
    if ("name" in value && typeof value.name === "string") return value.name;
    if ("title" in value && typeof value.title === "string") return value.title;
    if ("id" in value) return String(value.id);
  }
  return String(value);
}
