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
import type { CollectionTranslationKey, FieldOrigin, LabelText } from "@/modules/common/collection/config";
import type { TableListCardFieldLayout, TableListFeatures } from "../types";

export type ColumnMeta = {
  labelKey?: CollectionTranslationKey;
  label?: string;
  derivedLabel?: LabelText;
  origin?: Pick<FieldOrigin, "sourceLabel" | "via">;
  cardLabel?: ReactNode;
  card?: TableListCardFieldLayout;
  descriptionKey?: CollectionTranslationKey;
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

export function toFirstPage(pagination: TableListFeatures<unknown>["pagination"]): void {
  if (pagination !== false) pagination.onChange({ ...pagination.value, pageIndex: 0 });
}
