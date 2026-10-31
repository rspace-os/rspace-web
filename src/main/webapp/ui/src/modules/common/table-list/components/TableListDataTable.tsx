import {
  type ColumnOrderState,
  type ColumnVisibilityState,
  FlexRender,
  functionalUpdate,
  type SortingState,
  useTable,
} from "@tanstack/react-table";
import { memo, useCallback, useId, useMemo } from "react";
import { useTranslation } from "react-i18next";
import type { FieldName } from "@/modules/common/collection/config";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { Table, TableBody, TableCell, TableHeader, TableRow } from "@/modules/common/ui/table";
import { cn } from "@/modules/common/utils/cn";
import { useTableListColumnDefs } from "../hooks/columnDefs";
import type { FilterState, TableListPresentationVisibility, TableListProps } from "../types";
import { matchesFilters } from "../utils/clientFilters";
import { displayValue, selectionColumnId, tableListFeatures } from "../utils/features";
import { TableListCardGrid, type TableListCardItem } from "./TableListCardView";
import { TableListHeaderCell } from "./TableListHeaderCell";
import { TableListPagination } from "./TableListPagination";
import { TableListSelectionBar } from "./TableListSelectionBar";
import { TableListEmptyState, TableListErrorState } from "./TableListStates";

const noUiColumns = [] as const;
const StableTableBody = memo(TableBody);

function presentationClass(visibility: Exclude<TableListPresentationVisibility, false>): string | undefined {
  if (visibility === "narrow") return "@3xl/table-list:hidden";
  if (visibility === "wide") return "hidden @3xl/table-list:block";
  return undefined;
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
  | "presentations"
  | "renderRows"
  | "renderRowsWhenEmpty"
  | "emptyDescription"
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
  presentations,
  renderRows,
  renderRowsWhenEmpty = false,
  emptyDescription,
}: TableListDataTableProps<TDocument>) {
  if (selection && (!Number.isFinite(selection.maximumCount) || selection.maximumCount < 1)) {
    throw new Error("Table-list selection maximumCount must be positive");
  }
  const tablePresentation = presentations?.table ?? "all";
  const cardPresentation = presentations?.cards ?? false;
  if (!renderRows && tablePresentation === false && cardPresentation === false) {
    throw new Error("TableList must enable at least one presentation");
  }
  const { i18n, t } = useTranslation("common");
  const cardViewLabel = t("tableList.cardView", { collection: collectionLabel });
  const resizeId = useId();
  const listFields = useMemo(() => config.fields.filter((field) => field.list !== false), [config.fields]);
  const visibleFields = features.columns === false ? config.defaultColumns : features.columns.value;
  const visibleSet = useMemo(() => new Set(visibleFields), [visibleFields]);
  const filteringState = features.filtering === false ? false : features.filtering.value;
  const sortingState = features.sorting === false ? false : features.sorting.value;
  const paginationState = features.pagination === false ? false : features.pagination.value;
  const columnState = features.columns === false ? false : features.columns.value;
  const sortingEnabled = sortingState !== false;
  const hasSelection = selection !== undefined;

  const columns = useTableListColumnDefs({
    config,
    listFields,
    hasSelection,
    onRowOpen,
    sortingEnabled,
    uiColumns,
  });

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

  const rowModel = table.getRowModel();
  const visibleRows = useMemo(() => rowModel.rows.map((row) => row.original), [rowModel]);
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

  const resizeColumn = (columnId: string, size: number) =>
    table.setColumnSizing((current) => ({ ...current, [columnId]: size }));
  const selectAllPage = (
    <Checkbox
      aria-label={t("tableList.selection.selectAllPage")}
      checked={allVisibleSelected}
      indeterminate={someVisibleSelected}
      disabled={headerSelectionDisabled}
      onCheckedChange={updateVisibleSelection}
    />
  );

  const renderRowSelection = useCallback(
    (rowId: string, row: TDocument) => {
      if (!selection) return undefined;
      return (
        <Checkbox
          aria-label={t("tableList.selection.selectRow", {
            row: selection.getRowLabel?.(row) ?? rowId,
          })}
          checked={selection.value.has(rowId)}
          disabled={
            selection.disabled || (!selection.value.has(rowId) && selection.value.size >= selection.maximumCount)
          }
          onCheckedChange={(checked) => {
            const next = new Set(selection.value);
            checked ? next.add(rowId) : next.delete(rowId);
            selection.onChange(next);
          }}
        />
      );
    },
    [selection, t],
  );

  const renderedDataRows = useMemo(
    () =>
      renderRows
        ? []
        : rowModel.rows.map((row) => (
            <TableRow
              key={row.id}
              data-state={selection?.value.has(row.id) ? "selected" : undefined}
              className="hover:bg-muted/40 data-[state=selected]:bg-primary/10"
            >
              {row.getVisibleCells().map((cell) => (
                <TableCell key={cell.id} className="overflow-hidden py-2 text-ellipsis">
                  {cell.column.id === selectionColumnId && selection ? (
                    renderRowSelection(row.id, row.original)
                  ) : (
                    <FlexRender cell={cell} />
                  )}
                </TableCell>
              ))}
            </TableRow>
          )),
    [
      columnState,
      config,
      i18n.resolvedLanguage,
      onRowOpen,
      renderRowSelection,
      renderRows,
      rowModel,
      selection,
      t,
      uiColumns,
    ],
  );
  const renderedCardItems = useMemo<readonly TableListCardItem[]>(() => {
    if (renderRows || cardPresentation === false) return [];
    return rowModel.rows.map((row) => {
      const cells = row.getVisibleCells();
      const titleCell = row.getAllCells().find((cell) => cell.column.id === config.useAsTitle);
      const footerCells = cells.filter((cell) => cell.column.columnDef.meta?.card?.placement === "footer");
      const fields = cells
        .filter(
          (cell) =>
            cell.column.id !== selectionColumnId &&
            cell.column.id !== config.useAsTitle &&
            cell.column.columnDef.meta?.card?.placement !== "footer",
        )
        .map((cell) => ({
          id: cell.column.id,
          label: cell.column.columnDef.meta?.cardLabel ?? cell.column.id,
          value: <FlexRender cell={cell} />,
          fullWidth: cell.column.columnDef.meta?.card?.fullWidth,
        }));

      return {
        id: row.id,
        title: titleCell ? (
          <FlexRender cell={titleCell} />
        ) : (
          displayValue(row.original[config.useAsTitle], i18n.resolvedLanguage)
        ),
        fields,
        selection: renderRowSelection(row.id, row.original),
        actions:
          footerCells.length > 0 ? footerCells.map((cell) => <FlexRender key={cell.id} cell={cell} />) : undefined,
        selected: selection?.value.has(row.id),
      };
    });
  }, [
    cardPresentation,
    columnState,
    config,
    i18n.resolvedLanguage,
    onRowOpen,
    renderRows,
    rowModel,
    renderRowSelection,
    selection,
    t,
    uiColumns,
  ]);
  const loadingCardItems = useMemo<readonly TableListCardItem[]>(
    () =>
      Array.from({ length: 4 }, (_, index) => ({
        id: `loading-${index}`,
        title: <Skeleton className="h-5 w-2/3 rounded-sm" />,
        fields: [
          {
            id: "first",
            label: <Skeleton className="h-3 w-16 rounded-sm" />,
            value: <Skeleton className="h-5 w-full rounded-sm" />,
          },
          {
            id: "second",
            label: <Skeleton className="h-3 w-16 rounded-sm" />,
            value: <Skeleton className="h-5 w-full rounded-sm" />,
          },
        ],
      })),
    [],
  );
  const visibleColumns = table.getVisibleLeafColumns();
  const colSpan = Math.max(visibleColumns.length, 1);
  const columnResizing = table.atoms.columnResizing.get();

  return (
    <div
      className={cn("@container/table-list overflow-hidden", selection && selection.value.size > 0 && "pb-24 sm:pb-20")}
    >
      {renderRows ? (
        <section
          aria-label={t("tableList.view", { collection: collectionLabel })}
          aria-busy={status === "loading" || status === "refreshing"}
          className={reserveEmptyRows ? "min-h-90" : undefined}
        >
          {status === "loading" ? (
            <div className="space-y-3 py-3">
              {Array.from({ length: 4 }, (_, index) => (
                <Skeleton key={index} className="h-12 w-full rounded-sm" />
              ))}
            </div>
          ) : status === "error" ? (
            <TableListErrorState error={error} />
          ) : visibleRows.length === 0 && !renderRowsWhenEmpty ? (
            <TableListEmptyState description={emptyDescription} />
          ) : (
            renderRows(visibleRows)
          )}
        </section>
      ) : tablePresentation !== false ? (
        <div className={presentationClass(tablePresentation)}>
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
                  {group.headers.map((header) => (
                    <TableListHeaderCell
                      key={header.id}
                      header={header}
                      resizeId={resizeId}
                      resizeOffset={
                        columnResizing.isResizingColumn === header.column.id ? (columnResizing.deltaOffset ?? 0) : 0
                      }
                      selectAllControl={selection ? selectAllPage : undefined}
                      onResize={resizeColumn}
                    />
                  ))}
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
                    <TableListErrorState error={error} />
                  </TableCell>
                </TableRow>
              ) : rowModel.rows.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={colSpan} className={reserveEmptyRows ? "h-90" : "h-40"}>
                    <TableListEmptyState description={emptyDescription} />
                  </TableCell>
                </TableRow>
              ) : (
                renderedDataRows
              )}
            </StableTableBody>
          </Table>
        </div>
      ) : null}
      {!renderRows && cardPresentation !== false ? (
        <div className={presentationClass(cardPresentation)}>
          {status === "error" || (status !== "loading" && rowModel.rows.length === 0) ? (
            <section aria-label={cardViewLabel} className={reserveEmptyRows ? "min-h-90 py-3" : "min-h-40 py-3"}>
              {status === "error" ? (
                <TableListErrorState error={error} />
              ) : (
                <TableListEmptyState description={emptyDescription} />
              )}
            </section>
          ) : (
            <TableListCardGrid
              label={cardViewLabel}
              items={status === "loading" ? loadingCardItems : renderedCardItems}
              busy={status === "loading" || status === "refreshing"}
              selectionControl={
                selection && status !== "loading" ? (
                  <>
                    {selectAllPage}
                    <span aria-hidden="true">{t("tableList.selection.selectAllPage")}</span>
                  </>
                ) : undefined
              }
            />
          )}
        </div>
      ) : null}
      {status === "refreshing" ? (
        <div role="status" className="border-t bg-muted/30 px-3 py-1.5 text-xs text-foreground">
          {t("tableList.refreshing")}
        </div>
      ) : null}
      {features.pagination !== false ? (
        <TableListPagination
          value={features.pagination.value}
          rowCount={clientSide ? table.getPrePaginatedRowModel().rows.length : features.pagination.rowCount}
          hasNextPage={clientSide ? undefined : features.pagination.hasNextPage}
          pageRowCount={table.getRowModel().rows.length}
          limits={config.pagination?.limits}
          onChange={features.pagination.onChange}
        />
      ) : null}
      {selection && selection.value.size > 0 ? <TableListSelectionBar selection={selection} /> : null}
    </div>
  );
}
