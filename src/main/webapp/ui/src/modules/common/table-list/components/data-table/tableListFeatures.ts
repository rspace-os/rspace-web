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
import i18n from "@/modules/common/i18n";
import { formatList } from "@/modules/common/i18n/listFormat";
import { formatNumber } from "@/modules/common/i18n/localeFormat";
import type { TableListCardFieldLayout } from "../../state/tableListState";

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

export function displayValue(value: unknown, language: string | undefined, formatNumbers = false): string {
  if (value === null || value === undefined) return "";
  if (value instanceof Date) return value.toLocaleString(language);
  if (typeof value === "number") return formatNumbers ? formatNumber(value, language) : String(value);
  if (typeof value === "boolean")
    return value ? i18n.t("common:actions.yes", { lng: language }) : i18n.t("common:actions.no", { lng: language });
  if (Array.isArray(value))
    return formatList(
      value.map((item) => displayValue(item, language, formatNumbers)),
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
