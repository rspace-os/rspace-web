import { type ColumnDef, createColumnHelper } from "@tanstack/react-table";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import type { ResolvedFieldConfig } from "@/modules/common/collection/config";
import { fieldLabel, isIdLikeField } from "@/modules/common/collection/config";
import type { TableListProps, TableListUiColumn } from "../types";
import { displayValue, selectionColumnId, type tableListFeatures } from "../utils/features";

/** The table's column definitions: an optional selection column, the listed fields, then the UI columns. */
export function useTableListColumnDefs<TDocument extends Record<string, unknown>>({
  config,
  listFields,
  hasSelection,
  onRowOpen,
  sortingEnabled,
  translate,
  uiColumns,
}: {
  config: TableListProps<TDocument>["config"];
  listFields: readonly ResolvedFieldConfig<TDocument>[];
  hasSelection: boolean;
  onRowOpen: TableListProps<TDocument>["onRowOpen"];
  sortingEnabled: boolean;
  translate: (key: string) => string;
  uiColumns: readonly TableListUiColumn<TDocument>[];
}) {
  const { i18n } = useTranslation("common");
  const columnHelper = useMemo(() => createColumnHelper<typeof tableListFeatures, TDocument>(), []);
  return useMemo(
    () =>
      [
        ...(hasSelection
          ? [
              columnHelper.display({
                id: selectionColumnId,
                header: () => null,
                cell: () => null,
                size: 28,
                minSize: 28,
                maxSize: 28,
                enableSorting: false,
                enableResizing: false,
              }),
            ]
          : []),
        ...listFields.map((field) =>
          columnHelper.accessor((row) => row[field.name], {
            id: field.name,
            header: () => {
              const listConfig = field.list || undefined;
              return listConfig?.renderHeader
                ? listConfig.renderHeader({ config, field })
                : fieldLabel(field, translate);
            },
            cell: ({ row }) => {
              const value = row.original[field.name];
              const listConfig = field.list || undefined;
              const content = listConfig?.renderCell
                ? listConfig.renderCell({ config, field, row: row.original, value })
                : displayValue(
                    value,
                    i18n.resolvedLanguage ?? i18n.language,
                    field.type === "number" && field.name !== config.idField && !isIdLikeField(field.name),
                  );
              if (field.name === config.useAsTitle && onRowOpen) {
                return (
                  <button
                    type="button"
                    className="font-medium text-link hover:underline focus-visible:rounded-sm focus-visible:ring-2 focus-visible:ring-ring"
                    onClick={() => onRowOpen(row.original)}
                  >
                    {content}
                  </button>
                );
              }
              return content;
            },
            meta: {
              labelKey: field.labelKey,
              label: field.label,
              cardLabel: fieldLabel(field, translate),
              card: field.list ? field.list.card : undefined,
              descriptionKey: field.list ? field.list.descriptionKey : undefined,
              description: field.list ? field.list.description : undefined,
            },
            size: field.list ? field.list.width : undefined,
            minSize: field.list ? field.list.minWidth : undefined,
            enableSorting: sortingEnabled && field.capabilities.sortable,
          }),
        ),
        ...uiColumns.map((column) =>
          columnHelper.display({
            id: column.id,
            header: () => column.label,
            cell: ({ row }) => column.renderCell(row.original),
            size: column.width,
            minSize: column.minWidth,
            enableSorting: false,
            meta: {
              labelKey: column.id,
              // A column id is not a catalog key; `actions` would resolve to the common actions object.
              label: typeof column.label === "string" ? column.label : column.id,
              cardLabel: column.label,
              card: column.card,
            },
          }),
        ),
      ] as unknown as readonly ColumnDef<typeof tableListFeatures, TDocument, unknown>[],
    [columnHelper, config, hasSelection, listFields, onRowOpen, sortingEnabled, translate, uiColumns],
  );
}
