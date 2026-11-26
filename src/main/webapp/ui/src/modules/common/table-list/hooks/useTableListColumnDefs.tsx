import { type ColumnDef, createColumnHelper } from "@tanstack/react-table";
import type { Namespace } from "i18next";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import type { FieldValueSource, ResolvedFieldConfig } from "@/modules/common/collection/config";
import { useFieldLabels } from "@/modules/common/collection/useFieldLabels";
import { valueBehaviourOf } from "../fieldValueBehaviour";
import type { TableListProps, TableListUiColumn } from "../types";
import { type DisplayFormat, displayValue } from "../utils/displayValue";
import { selectionColumnId, type tableListFeatures } from "../utils/features";

/** The raw value a derived column reads from its relationship target or runtime-field response. */
function sourceValue<TDocument extends Record<string, unknown>>(
  row: TDocument,
  source: FieldValueSource<TDocument>,
): unknown {
  const holder = source.kind === "relationshipTarget" ? row[source.owner] : row[source.responseField];
  if (typeof holder !== "object" || holder === null || Array.isArray(holder)) return undefined;
  if (source.kind === "runtimeField") return (holder as Record<string, unknown>)[source.id];
  const target = (holder as Record<string, unknown>).value;
  if (typeof target !== "object" || target === null || Array.isArray(target)) return undefined;
  return (target as Record<string, unknown>)[source.targetField];
}

/** Identifiers stay unformatted; other numbers, instants, dates and times follow the viewer's language. */
function displayFormat<TDocument>(field: ResolvedFieldConfig<TDocument>, idField: string): DisplayFormat | undefined {
  const valueSource = field.list ? field.list.valueSource : undefined;
  const identifier =
    field.name === idField || (valueSource?.kind === "relationshipTarget" && valueSource.targetField === "id");
  const { displayFormat: resolved } = valueBehaviourOf(field);
  return identifier && resolved === "number" ? undefined : resolved;
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
  const language = i18n.resolvedLanguage ?? i18n.language;
  const { fieldLabel } = useFieldLabels();
  return useMemo(() => {
    const columnHelper = createColumnHelper<typeof tableListFeatures, TDocument>();
    const locale = { language, yes: t("actions.yes"), no: t("actions.no") };
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
              : displayValue(
                  listConfig?.valueSource ? sourceValue(row.original, listConfig.valueSource) : value,
                  locale,
                  displayFormat(field, config.idField),
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
            item: { ...listConfig?.item, label: fieldLabel(field) },
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
            label: typeof column.label === "string" ? column.label : column.id,
            item: { ...column.item, label: column.label },
          },
        }),
      ),
    ] as unknown as readonly ColumnDef<typeof tableListFeatures, TDocument, unknown>[];
  }, [language, t, config, hasSelection, listFields, onRowOpen, sortingEnabled, uiColumns, fieldLabel]);
}
