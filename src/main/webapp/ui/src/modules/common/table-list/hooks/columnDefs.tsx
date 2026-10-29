import { type ColumnDef, createColumnHelper } from "@tanstack/react-table";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import type { ResolvedFieldConfig } from "@/modules/common/collection/config";
import { fieldLabel } from "@/modules/common/collection/labels";
import { isIdLikeField } from "@/modules/common/collection/utils";
import type { TableListProps, TableListUiColumn } from "../types";
import { displayValue, selectionColumnId, type tableListFeatures } from "../utils/features";

/** The table's column definitions: an optional selection column, the listed fields, then the UI columns. */
export function useTableListColumnDefs<TDocument extends Record<string, unknown>>({
  config,
  listFields,
  hasSelection,
  onRowOpen,
  sortingEnabled,
  uiColumns,
}: {
  config: TableListProps<TDocument>["config"];
  listFields: readonly ResolvedFieldConfig<TDocument>[];
  hasSelection: boolean;
  onRowOpen: TableListProps<TDocument>["onRowOpen"];
  sortingEnabled: boolean;
  uiColumns: readonly TableListUiColumn<TDocument>[];
}) {
  const { i18n, t } = useTranslation("common");
  return useMemo(() => {
    const columnHelper = createColumnHelper<typeof tableListFeatures, TDocument>();
    const translate = (key: string) => (i18n.exists(key) ? t(key as never) : key);
    return [
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
      ...listFields.map((field) => {
        const listConfig = field.list || undefined;
        return columnHelper.accessor((row) => row[field.name], {
          id: field.name,
          header: () => {
            return listConfig?.renderHeader ? listConfig.renderHeader({ config, field }) : fieldLabel(field, translate);
          },
          cell: ({ row }) => {
            const value = row.original[field.name];
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
            card: listConfig?.card,
            descriptionKey: listConfig?.descriptionKey,
            description: listConfig?.description,
          },
          size: listConfig?.width,
          minSize: listConfig?.minWidth,
          enableSorting: sortingEnabled && field.capabilities.sortable,
        });
      }),
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
    ] as unknown as readonly ColumnDef<typeof tableListFeatures, TDocument, unknown>[];
  }, [i18n, t, config, hasSelection, listFields, onRowOpen, sortingEnabled, uiColumns]);
}
