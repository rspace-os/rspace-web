import {
  type ColumnDef,
  type ColumnOrderState,
  type ColumnVisibilityState,
  columnFilteringFeature,
  columnOrderingFeature,
  columnResizingFeature,
  columnSizingFeature,
  columnVisibilityFeature,
  createColumnHelper,
  createFilteredRowModel,
  createPaginatedRowModel,
  createSortedRowModel,
  FlexRender,
  functionalUpdate,
  globalFilteringFeature,
  rowPaginationFeature,
  rowSortingFeature,
  type SortingState,
  tableFeatures,
  useTable,
} from "@tanstack/react-table";
import { ArrowDownIcon, ArrowUpDownIcon, ArrowUpIcon, CircleHelpIcon } from "lucide-react";
import { memo, useCallback, useId, useMemo, useRef } from "react";
import { useTranslation } from "react-i18next";
import type { FieldName, SearchSelector } from "@/modules/common/collection/collectionConfig";
import { fieldLabel } from "@/modules/common/collection/collectionConfig";
import { Button } from "@/modules/common/ui/button";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/modules/common/ui/empty";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/modules/common/ui/table";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { cn } from "@/modules/common/utils/cn";
import type { FilterExpression, FilterState, TableListProps } from "../tableListState";
import { TableListPagination } from "./pagination/TableListPagination";

type ColumnMeta = {
  labelKey: string;
  label?: string;
  descriptionKey?: string;
  description?: string;
};

const noUiColumns = [] as const;
const selectionColumnId = "table-list-selection";
const StableTableBody = memo(TableBody);

const tableListFeatures = tableFeatures({
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

function comparable(value: unknown): string | number | boolean {
  if (value instanceof Date) return value.getTime();
  if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") return value;
  return String(value ?? "");
}

function equal(left: unknown, right: unknown): boolean {
  return typeof left === "string" && typeof right === "string"
    ? left.localeCompare(right, undefined, { sensitivity: "accent" }) === 0
    : comparable(left) === comparable(right);
}

function wildcardPattern(value: string): RegExp {
  const escaped = value.replace(/[.+?^${}()|[\]\\]/g, "\\$&").replaceAll("*", ".*");
  return new RegExp(`^${escaped}$`, "i");
}

function matchesExpression<TDocument extends Record<string, unknown>>(
  row: TDocument,
  expression: FilterExpression<TDocument>,
): boolean {
  if (expression.kind !== "comparison") {
    return expression.kind === "and"
      ? expression.children.every((child) => matchesExpression(row, child))
      : expression.children.some((child) => matchesExpression(row, child));
  }

  const rowValue = row[String(expression.field)];
  const filterValues = Array.isArray(expression.value) ? expression.value : [expression.value];
  switch (expression.operator) {
    case "equals":
      return equal(rowValue, expression.value);
    case "notEquals":
      return !equal(rowValue, expression.value);
    case "greaterThan":
      return comparable(rowValue) > comparable(expression.value);
    case "greaterThanOrEqual":
      return comparable(rowValue) >= comparable(expression.value);
    case "lessThan":
      return comparable(rowValue) < comparable(expression.value);
    case "lessThanOrEqual":
      return comparable(rowValue) <= comparable(expression.value);
    case "in":
      return filterValues.some((value) => equal(rowValue, value));
    case "notIn":
      return filterValues.every((value) => !equal(rowValue, value));
    case "contains":
      return String(rowValue ?? "")
        .toLocaleLowerCase()
        .includes(String(expression.value).toLocaleLowerCase());
    case "matches":
      return wildcardPattern(String(expression.value)).test(String(rowValue ?? ""));
    case "exists":
      return expression.value
        ? rowValue !== null && rowValue !== undefined
        : rowValue === null || rowValue === undefined;
  }
}

function searchValues<TDocument extends Record<string, unknown>>(
  row: TDocument,
  selector: SearchSelector<TDocument>,
): readonly unknown[] {
  const name = String(selector);
  const dot = name.indexOf(".");
  if (dot < 0) return [row[name]];
  const relationship = row[name.slice(0, dot)];
  if (typeof relationship !== "object" || relationship === null || Array.isArray(relationship)) return [];
  const value = (relationship as Record<string, unknown>).value;
  const targets = Array.isArray(value) ? value : [value];
  return targets.flatMap((target) =>
    typeof target === "object" && target !== null && !Array.isArray(target)
      ? [(target as Record<string, unknown>)[name.slice(dot + 1)]]
      : [],
  );
}

function matchesFilters<TDocument extends Record<string, unknown>>(
  row: TDocument,
  config: TableListProps<TDocument>["config"],
  filters: FilterState<TDocument>,
): boolean {
  const search = filters.search.trim().toLocaleLowerCase();
  const matchesSearch =
    search === "" ||
    (config.listSearchableFields ?? []).some((field) =>
      searchValues(row, field).some((value) =>
        String(value ?? "")
          .toLocaleLowerCase()
          .includes(search),
      ),
    );
  return matchesSearch && (!filters.expression || matchesExpression(row, filters.expression));
}

function displayValue(value: unknown): string {
  if (value === null || value === undefined) return "";
  if (value instanceof Date) return value.toLocaleString();
  if (Array.isArray(value)) return value.map(displayValue).join(", ");
  if (typeof value === "object") {
    if ("name" in value && typeof value.name === "string") return value.name;
    if ("title" in value && typeof value.title === "string") return value.title;
    if ("id" in value) return String(value.id);
  }
  return String(value);
}

function SortIcon({ direction }: { direction: false | "asc" | "desc" }) {
  if (direction === "asc") return <ArrowUpIcon aria-hidden="true" />;
  if (direction === "desc") return <ArrowDownIcon aria-hidden="true" />;
  return <ArrowUpDownIcon aria-hidden="true" className="opacity-45" />;
}

type TableListDataTableProps<TDocument extends Record<string, unknown>> = Pick<
  TableListProps<TDocument>,
  | "config"
  | "rows"
  | "getRowId"
  | "features"
  | "clientSide"
  | "status"
  | "error"
  | "onRowOpen"
  | "reserveEmptyRows"
  | "selection"
  | "uiColumns"
> & {
  collectionLabel: string;
};

export function TableListDataTable<TDocument extends Record<string, unknown>>({
  config,
  rows,
  getRowId,
  features,
  clientSide = false,
  status = "idle",
  error,
  onRowOpen,
  collectionLabel,
  reserveEmptyRows = true,
  selection,
  uiColumns = noUiColumns,
}: TableListDataTableProps<TDocument>) {
  if (selection && (!Number.isFinite(selection.maximumCount) || selection.maximumCount < 1)) {
    throw new Error("Table-list selection maximumCount must be positive");
  }
  const { i18n, t } = useTranslation("common");
  const resizeId = useId();
  const translate = useCallback((key: string) => (i18n.exists(key) ? t(key as never) : key), [i18n, t]);
  const columnHelper = useMemo(() => createColumnHelper<typeof tableListFeatures, TDocument>(), []);
  const listFields = useMemo(() => config.fields.filter((field) => field.list !== false), [config.fields]);
  const visibleFields = features.columns === false ? config.defaultColumns : features.columns.value;
  const visibleSet = useMemo(() => new Set(visibleFields), [visibleFields]);
  const filteringState = features.filtering === false ? false : features.filtering.value;
  const sortingState = features.sorting === false ? false : features.sorting.value;
  const paginationState = features.pagination === false ? false : features.pagination.value;
  const columnState = features.columns === false ? false : features.columns.value;
  const localFilteringState = clientSide ? filteringState : false;
  const localSortingState = clientSide ? sortingState : false;
  const localPaginationState = clientSide ? paginationState : false;
  const sortingEnabled = sortingState !== false;
  const hasSelection = selection !== undefined;

  const columns = useMemo(
    () =>
      [
        ...(hasSelection
          ? [
              columnHelper.display({
                id: selectionColumnId,
                header: () => null,
                cell: () => null,
                size: 44,
                minSize: 44,
                maxSize: 44,
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
                : displayValue(value);
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
          }),
        ),
      ] as unknown as readonly ColumnDef<typeof tableListFeatures, TDocument, unknown>[],
    [columnHelper, config, hasSelection, listFields, onRowOpen, sortingEnabled, translate, uiColumns],
  );

  const sorting: SortingState = useMemo(
    () =>
      sortingState === false ? [] : sortingState.map((rule) => ({ id: rule.field, desc: rule.direction === "desc" })),
    [sortingState],
  );
  const columnOrder: ColumnOrderState = useMemo(
    () => [
      ...(hasSelection ? [selectionColumnId] : []),
      ...visibleFields,
      ...listFields.map((field) => field.name).filter((field) => !visibleSet.has(field)),
    ],
    [hasSelection, listFields, visibleFields, visibleSet],
  );
  const columnVisibility: ColumnVisibilityState = useMemo(
    () => Object.fromEntries(listFields.map((field) => [field.name, visibleSet.has(field.name)])),
    [listFields, visibleSet],
  );
  const pagination = useMemo(
    () => (paginationState === false ? { pageIndex: 0, pageSize: rows.length || 1 } : paginationState),
    [paginationState, rows.length],
  );
  const globalFilter = filteringState === false ? undefined : filteringState;

  const table = useTable<typeof tableListFeatures, TDocument>({
    features: tableListFeatures,
    columns,
    data: rows,
    getRowId,
    state: {
      sorting,
      columnOrder,
      columnVisibility,
      pagination,
      globalFilter,
    },
    globalFilterFn: (row, _columnId, value: FilterState<TDocument>) => matchesFilters(row.original, config, value),
    getColumnCanGlobalFilter: (column) => column.id === listFields[0]?.name,
    manualFiltering: !clientSide,
    manualSorting: !clientSide,
    manualPagination: !clientSide,
    rowCount: features.pagination === false ? rows.length : features.pagination.rowCount,
    enableMultiSort: true,
    columnResizeMode: "onEnd",
    onSortingChange:
      features.sorting === false
        ? undefined
        : (updater) => {
            const next = functionalUpdate(updater, sorting).map((rule) => ({
              field: rule.id as FieldName<TDocument>,
              direction: rule.desc ? ("desc" as const) : ("asc" as const),
            }));
            features.sorting !== false && features.sorting.onChange(next);
            if (features.pagination !== false)
              features.pagination.onChange({ ...features.pagination.value, pageIndex: 0 });
          },
  });

  // Transient resize state refreshes the table wrapper; only row-affecting inputs should rebuild thousands of cells.
  const tableRef = useRef(table);
  tableRef.current = table;
  const rowModel = useMemo(
    () => tableRef.current.getRowModel(),
    [
      rows,
      config,
      uiColumns,
      onRowOpen,
      clientSide,
      localFilteringState,
      localSortingState,
      localPaginationState,
      columnState,
      i18n.resolvedLanguage,
    ],
  );
  const visibleRowIds = useMemo(() => rowModel.rows.map((row) => row.id), [rowModel]);
  const selectedVisibleCount = selection ? visibleRowIds.filter((rowId) => selection.value.has(rowId)).length : 0;
  const allVisibleSelected = visibleRowIds.length > 0 && selectedVisibleCount === visibleRowIds.length;
  const someVisibleSelected = selectedVisibleCount > 0 && !allVisibleSelected;
  const missingVisibleCount = visibleRowIds.length - selectedVisibleCount;
  const headerSelectionDisabled =
    selection === undefined ||
    selection.disabled === true ||
    visibleRowIds.length === 0 ||
    (!allVisibleSelected && selection.value.size + missingVisibleCount > selection.maximumCount);

  const updateVisibleSelection = (checked: boolean) => {
    if (!selection) return;
    const next = new Set(selection.value);
    visibleRowIds.forEach((rowId) => {
      if (checked) next.add(rowId);
      else next.delete(rowId);
    });
    selection.onChange(next);
  };

  const renderedDataRows = useMemo(
    () =>
      rowModel.rows.map((row) => (
        <TableRow
          key={row.id}
          data-state={selection?.value.has(row.id) ? "selected" : undefined}
          className="hover:bg-muted/40 data-[state=selected]:bg-primary/10"
        >
          {row.getVisibleCells().map((cell) => (
            <TableCell key={cell.id} className="overflow-hidden py-2 text-ellipsis">
              {cell.column.id === selectionColumnId && selection ? (
                <Checkbox
                  aria-label={t("tableList.selection.selectRow", {
                    row: selection.getRowLabel?.(row.original) ?? row.id,
                  })}
                  checked={selection.value.has(row.id)}
                  disabled={
                    selection.disabled ||
                    (!selection.value.has(row.id) && selection.value.size >= selection.maximumCount)
                  }
                  onCheckedChange={(checked) => {
                    const next = new Set(selection.value);
                    checked ? next.add(row.id) : next.delete(row.id);
                    selection.onChange(next);
                  }}
                />
              ) : (
                <FlexRender cell={cell} />
              )}
            </TableCell>
          ))}
        </TableRow>
      )),
    [columnState, config, i18n.resolvedLanguage, onRowOpen, rowModel, selection, t, uiColumns],
  );
  const visibleColumns = table.getVisibleLeafColumns();
  const colSpan = Math.max(visibleColumns.length, 1);
  const columnResizing = table.atoms.columnResizing.get();

  return (
    <div className={cn("overflow-hidden", selection && selection.value.size > 0 && "pb-24 sm:pb-20")}>
      <Table
        aria-label={t("tableList.view", { collection: collectionLabel })}
        aria-busy={status === "loading" || status === "refreshing"}
        className="table-fixed"
        style={{ width: table.getTotalSize(), minWidth: "100%" }}
      >
        <colgroup>
          {visibleColumns.map((column) => (
            <col key={column.id} style={{ width: column.getSize() }} />
          ))}
        </colgroup>
        <TableHeader>
          {table.getHeaderGroups().map((group) => (
            <TableRow key={group.id} className="hover:bg-transparent">
              {group.headers.map((header) => {
                const direction = header.column.getIsSorted();
                const meta = header.column.columnDef.meta;
                const label = meta ? fieldLabel(meta, translate) : header.column.id;
                const description = meta?.description ?? (meta?.descriptionKey ? translate(meta.descriptionKey) : null);
                const headerLabelId = `${resizeId}-${header.id}-label`;
                const resizeLabelId = `${resizeId}-${header.id}-resize`;
                const resizeOffset =
                  columnResizing.isResizingColumn === header.column.id ? (columnResizing.deltaOffset ?? 0) : 0;
                return (
                  <TableHead
                    key={header.id}
                    aria-labelledby={headerLabelId}
                    aria-sort={
                      header.column.getCanSort()
                        ? direction === "asc"
                          ? "ascending"
                          : direction === "desc"
                            ? "descending"
                            : "none"
                        : undefined
                    }
                    className="relative h-9 bg-muted/35 pr-4 text-xs"
                  >
                    <div className="flex min-w-0 items-center gap-1.5 overflow-hidden">
                      {header.column.id === selectionColumnId && selection ? (
                        <span id={headerLabelId}>
                          <Checkbox
                            aria-label={t("tableList.selection.selectAllPage")}
                            checked={allVisibleSelected}
                            indeterminate={someVisibleSelected}
                            disabled={headerSelectionDisabled}
                            onCheckedChange={updateVisibleSelection}
                          />
                        </span>
                      ) : header.column.getCanSort() ? (
                        <button
                          type="button"
                          aria-label={t("tableList.actions.sortBy", { column: label })}
                          className="flex min-w-0 items-center gap-1 rounded-sm hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring"
                          onClick={header.column.getToggleSortingHandler()}
                        >
                          <span id={headerLabelId} className="min-w-0 overflow-hidden text-ellipsis">
                            <FlexRender header={header} />
                          </span>
                          <SortIcon direction={direction} />
                        </button>
                      ) : (
                        <span id={headerLabelId} className="min-w-0 overflow-hidden text-ellipsis">
                          <FlexRender header={header} />
                        </span>
                      )}
                      {description ? (
                        <Tooltip>
                          <TooltipTrigger
                            render={
                              <button
                                type="button"
                                aria-label={t("tableList.tooltip", { column: label })}
                                className="rounded-sm text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring"
                              />
                            }
                          >
                            <CircleHelpIcon aria-hidden="true" className="size-3!" />
                          </TooltipTrigger>
                          <TooltipContent side="bottom" align="start" className="rounded-sm">
                            {description}
                          </TooltipContent>
                        </Tooltip>
                      ) : null}
                    </div>
                    {header.column.id !== selectionColumnId && header.column.getCanResize() ? (
                      <button
                        type="button"
                        aria-labelledby={`${resizeLabelId} ${headerLabelId}`}
                        className="group absolute inset-y-0 -right-1 z-10 w-2 cursor-col-resize touch-none select-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                        onMouseDown={header.getResizeHandler()}
                        onTouchStart={header.getResizeHandler()}
                        onDoubleClick={() => header.column.resetSize()}
                        onKeyDown={(event) => {
                          if (event.key === "Enter" || event.key === " ") {
                            event.preventDefault();
                            header.column.resetSize();
                            return;
                          }
                          if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
                          event.preventDefault();
                          const minimum = header.column.columnDef.minSize ?? 20;
                          const maximum = header.column.columnDef.maxSize ?? Number.MAX_SAFE_INTEGER;
                          const delta = event.key === "ArrowLeft" ? -16 : 16;
                          const size = Math.min(maximum, Math.max(minimum, header.column.getSize() + delta));
                          table.setColumnSizing((current) => ({ ...current, [header.column.id]: size }));
                        }}
                      >
                        <span id={resizeLabelId} className="sr-only">
                          {t("tableList.actions.resizeColumn")}
                        </span>
                        <span
                          aria-hidden="true"
                          data-active={header.column.getIsResizing()}
                          className="mx-auto block h-full w-px bg-border group-hover:bg-primary group-focus-visible:bg-primary data-[active=true]:bg-primary"
                          style={{ transform: `translateX(${resizeOffset}px)` }}
                        />
                      </button>
                    ) : null}
                  </TableHead>
                );
              })}
            </TableRow>
          ))}
        </TableHeader>
        <StableTableBody>
          {status === "loading" ? (
            Array.from({ length: 5 }, (_, index) => (
              <TableRow key={index}>
                <TableCell colSpan={colSpan}>
                  <Skeleton className="h-5 w-full rounded-sm" />
                </TableCell>
              </TableRow>
            ))
          ) : status === "error" ? (
            <TableRow>
              <TableCell colSpan={colSpan} className="h-40">
                <Empty className="rounded-sm border-0 p-6">
                  <EmptyHeader>
                    <EmptyTitle>{t("tableList.error.title")}</EmptyTitle>
                    <EmptyDescription>
                      {error instanceof Error ? error.message : t("tableList.error.description")}
                    </EmptyDescription>
                  </EmptyHeader>
                </Empty>
              </TableCell>
            </TableRow>
          ) : rowModel.rows.length === 0 ? (
            <TableRow>
              <TableCell colSpan={colSpan} className={reserveEmptyRows ? "h-90" : "h-40"}>
                <Empty className="rounded-sm border-0 p-6">
                  <EmptyHeader>
                    <EmptyTitle>{t("tableList.empty.title")}</EmptyTitle>
                    <EmptyDescription>{t("tableList.empty.description")}</EmptyDescription>
                  </EmptyHeader>
                </Empty>
              </TableCell>
            </TableRow>
          ) : (
            renderedDataRows
          )}
        </StableTableBody>
      </Table>
      {status === "refreshing" ? (
        <div role="status" className="border-t bg-muted/30 px-3 py-1.5 text-xs text-foreground">
          {t("tableList.refreshing")}
        </div>
      ) : null}
      {features.pagination !== false ? (
        <TableListPagination
          value={features.pagination.value}
          rowCount={clientSide ? table.getPrePaginatedRowModel().rows.length : features.pagination.rowCount}
          limits={config.pagination?.limits}
          onChange={features.pagination.onChange}
        />
      ) : null}
      {selection && selection.value.size > 0 ? (
        <section
          aria-label={t("tableList.selection.regionLabel")}
          className="fixed inset-x-4 bottom-4 z-50 mx-auto flex w-fit max-w-[calc(100vw-2rem)] flex-wrap items-center justify-center gap-2 rounded-sm border bg-popover p-2 text-popover-foreground shadow-lg"
        >
          <output aria-live="polite" aria-atomic="true" className="px-1 text-sm font-medium">
            {t("tableList.selection.count", {
              count: selection.value.size,
            })}
          </output>
          {selection.value.size >= selection.maximumCount ? (
            <span className="text-xs text-muted-foreground">
              {t("tableList.selection.limit", {
                count: selection.maximumCount,
              })}
            </span>
          ) : null}
          <div className="flex flex-wrap items-center justify-center gap-1">
            {selection.renderActions({
              selectedRowIds: selection.value,
              clearSelection: () => selection.onChange(new Set()),
            })}
            <Button
              type="button"
              variant="ghost"
              size="sm"
              disabled={selection.disabled}
              onClick={() => selection.onChange(new Set())}
            >
              {t("tableList.selection.clear")}
            </Button>
          </div>
        </section>
      ) : null}
    </div>
  );
}
