import { type ColumnDef, createColumnHelper } from "@tanstack/react-table";
import type { Namespace } from "i18next";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import type { FieldValueSource, ResolvedFieldConfig } from "@/modules/common/collection/config";
import { useFieldLabels } from "@/modules/common/collection/useFieldLabels";
import { formatList } from "@/modules/common/i18n/listFormat";
import { formatNumber } from "@/modules/common/i18n/localeFormat";
import type { TableListProps, TableListUiColumn } from "../types";
import { displayValue, selectionColumnId, type tableListFeatures } from "../utils/features";

const VALUE_LIST: Intl.ListFormatOptions = { type: "unit", style: "short" };

function valueFromSource<TDocument extends Record<string, unknown>>(
  row: TDocument,
  source: FieldValueSource<TDocument>,
  language: string | undefined,
  yes: string,
  no: string,
): string {
  if (source.kind === "relationshipTarget") {
    const relationship = row[source.owner];
    if (typeof relationship !== "object" || relationship === null || Array.isArray(relationship)) return "";
    const target = (relationship as Record<string, unknown>).value;
    if (typeof target !== "object" || target === null || Array.isArray(target)) return "";
    const value = (target as Record<string, unknown>)[source.targetField];
    if (typeof value === "boolean") return value ? yes : no;
    if (typeof value === "number" && source.targetField !== "id") {
      return formatNumber(value, language);
    }
    return value === null || value === undefined ? "" : String(value);
  }

  const response = row[source.responseField];
  if (typeof response !== "object" || response === null || Array.isArray(response)) return "";
  const value = (response as Record<string, unknown>)[source.id];
  if (value === null || value === undefined) return "";
  const format = (item: unknown) =>
    source.numeric && typeof item === "number" ? formatNumber(item, language) : String(item);
  return Array.isArray(value) ? formatList(value.map(format), language, VALUE_LIST) : format(value);
}

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
  const { i18n, t } = useTranslation<Namespace>("common");
  const { fieldLabel } = useFieldLabels();
  return useMemo(() => {
    const columnHelper = createColumnHelper<typeof tableListFeatures, TDocument>();
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
            return listConfig?.renderHeader ? listConfig.renderHeader({ config, field }) : fieldLabel(field);
          },
          cell: ({ row }) => {
            const value = row.original[field.name];
            const content = listConfig?.renderCell
              ? listConfig.renderCell({ config, field, row: row.original, value })
              : listConfig?.valueSource
                ? valueFromSource(
                    row.original,
                    listConfig.valueSource,
                    i18n.resolvedLanguage ?? i18n.language,
                    t("actions.yes"),
                    t("actions.no"),
                  )
                : displayValue(
                    value,
                    i18n.resolvedLanguage ?? i18n.language,
                    field.type === "number" && field.name !== config.idField,
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
            derivedLabel: field.derivedLabel,
            origin: field.origin,
            cardLabel: fieldLabel(field),
            card: listConfig?.card,
            widthMode: listConfig?.widthMode,
            descriptionKey: listConfig?.descriptionKey,
            description: listConfig?.description,
          },
          size: listConfig?.width,
          minSize: listConfig?.minWidth,
          maxSize: listConfig?.maxWidth,
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
          maxSize: column.maxWidth,
          enableSorting: false,
          meta: {
            labelKey: column.id,
            // A column id is not a catalog key; `actions` would resolve to the common actions object.
            label: typeof column.label === "string" ? column.label : column.id,
            cardLabel: column.label,
            card: column.card,
            widthMode: column.widthMode,
          },
        }),
      ),
    ] as unknown as readonly ColumnDef<typeof tableListFeatures, TDocument, unknown>[];
  }, [i18n, t, config, hasSelection, listFields, onRowOpen, sortingEnabled, uiColumns, fieldLabel]);
}
