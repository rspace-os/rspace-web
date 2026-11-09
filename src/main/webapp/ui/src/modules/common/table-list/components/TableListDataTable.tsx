import {
  type ColumnOrderState,
  type ColumnVisibilityState,
  FlexRender,
  functionalUpdate,
  type SortingState,
  useTable,
} from "@tanstack/react-table";
import { type ReactNode, useCallback, useId, useMemo } from "react";
import { useTranslation } from "react-i18next";
import type { FieldName } from "@/modules/common/collection/config";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { Table, TableBody, TableCell, TableHeader, TableRow } from "@/modules/common/ui/table";
import { cn } from "@/modules/common/utils/cn";
import { useTableListColumnDefs } from "../hooks/useTableListColumnDefs";
import type { FilterState, TableListPresentationVisibility, TableListProps, TableListStatus } from "../types";
import { matchesFilters } from "../utils/clientFilters";
import { displayValue } from "../utils/displayValue";
import { selectionColumnId, tableListFeatures, toFirstPage } from "../utils/features";
import type { TableListCardItem } from "./TableListCard";
import { TableListCardGrid } from "./TableListCardGrid";
import { TableListHeaderCell } from "./TableListHeaderCell";
import { TableListPagination } from "./TableListPagination";
import { TableListSelectionBar } from "./TableListSelectionBar";
import { TableListEmptyState, TableListErrorState } from "./TableListStates";

const noUiColumns = [] as const;
const loadingCardItems: readonly TableListCardItem[] = Array.from({ length: 4 }, (_, index) => ({
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
}));

function presentationClass(visibility: Exclude<TableListPresentationVisibility, false>): string | undefined {
  if (visibility === "narrow") return "@3xl/table-list:hidden";
  if (visibility === "wide") return "hidden @3xl/table-list:block";
  return undefined;
}

type BodyState = "loading" | "error" | "empty" | "rows";

function bodyStateFor(status: TableListStatus, rowCount: number, rowsWhenEmpty: boolean): BodyState {
  if (status === "loading") return "loading";
  if (status === "error") return "error";
  if (rowCount === 0 && !rowsWhenEmpty) return "empty";
  return "rows";
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
  const sortingFeature = features.sorting;
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
  const sortingState = sortingFeature === false ? false : sortingFeature.value;
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
    globalFilterFn: (row, _columnId, value: FilterState<TDocument>) =>
      matchesFilters(row.original, config, value, i18n.resolvedLanguage ?? i18n.language),
    getColumnCanGlobalFilter: (column) => column.id === listFields[0]?.name,
    manualFiltering: !clientSide,
    manualSorting: !clientSide,
    manualPagination: !clientSide,
    rowCount: features.pagination === false ? rows.length : features.pagination.rowCount,
    enableMultiSort: true,
    columnResizeMode: "onEnd",
    onSortingChange:
      sortingFeature === false
        ? undefined
        : (updater) => {
            const next = functionalUpdate(updater, sorting).map((rule) => ({
              field: rule.id as FieldName<TDocument>,
              direction: rule.desc ? ("desc" as const) : ("asc" as const),
            }));
            sortingFeature.onChange(next);
            toFirstPage(features.pagination);
          },
  });

  const rowModel = table.getRowModel();
  const visibleRows = rowModel.rows.map((row) => row.original);
  const visibleRowIds = rowModel.rows.map((row) => row.id);
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
              className="group/row hover:bg-muted/40 data-[state=selected]:bg-primary/10"
            >
              {row.getVisibleCells().map((cell) => (
                <TableCell
                  key={cell.id}
                  className={cn(
                    "overflow-hidden py-2 text-ellipsis",
                    cell.column.id === "actions" &&
                      "sticky right-0 z-10 bg-background group-hover/row:bg-[color-mix(in_oklab,var(--color-muted)_40%,var(--color-background))] group-data-[state=selected]/row:bg-[color-mix(in_oklab,var(--color-primary)_10%,var(--color-background))]",
                  )}
                >
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
      columns,
      columnState,
      config,
      i18n.resolvedLanguage,
      i18n.language,
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
          displayValue(row.original[config.useAsTitle], {
            language: i18n.resolvedLanguage ?? i18n.language,
            yes: t("actions.yes"),
            no: t("actions.no"),
          })
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
    columns,
    columnState,
    config,
    i18n.resolvedLanguage,
    i18n.language,
    onRowOpen,
    renderRows,
    rowModel,
    renderRowSelection,
    selection,
    t,
    uiColumns,
  ]);
  const visibleColumns = table.getVisibleLeafColumns();
  const colSpan = Math.max(visibleColumns.length, 1);
  const columnResizing = table.atoms.columnResizing.get();

  const renderCustomRows = (render: (rows: readonly TDocument[]) => ReactNode): ReactNode => {
    switch (bodyStateFor(status, visibleRows.length, renderRowsWhenEmpty)) {
      case "loading":
        return (
          <div className="space-y-3 py-3">
            {Array.from({ length: 4 }, (_, index) => (
              <Skeleton key={index} className="h-12 w-full rounded-sm" />
            ))}
          </div>
        );
      case "error":
        return <TableListErrorState error={error} />;
      case "empty":
        return <TableListEmptyState description={emptyDescription} />;
      case "rows":
        return render(visibleRows);
    }
  };

  const renderTableBody = (): ReactNode => {
    switch (bodyStateFor(status, rowModel.rows.length, false)) {
      case "loading":
        return Array.from({ length: 5 }, (_, index) => (
          <TableRow key={index}>
            <TableCell colSpan={colSpan}>
              <Skeleton className="h-5 w-full rounded-sm" />
            </TableCell>
          </TableRow>
        ));
      case "error":
        return (
          <TableRow>
            <TableCell colSpan={colSpan} className="h-40">
              <TableListErrorState error={error} />
            </TableCell>
          </TableRow>
        );
      case "empty":
        return (
          <TableRow>
            <TableCell colSpan={colSpan} className={reserveEmptyRows ? "h-90" : "h-40"}>
              <TableListEmptyState description={emptyDescription} />
            </TableCell>
          </TableRow>
        );
      case "rows":
        return renderedDataRows;
    }
  };

  const renderCards = (): ReactNode => {
    const cardState = bodyStateFor(status, rowModel.rows.length, false);
    switch (cardState) {
      case "error":
      case "empty":
        return (
          <section aria-label={cardViewLabel} className={reserveEmptyRows ? "min-h-90 py-3" : "min-h-40 py-3"}>
            {cardState === "error" ? (
              <TableListErrorState error={error} />
            ) : (
              <TableListEmptyState description={emptyDescription} />
            )}
          </section>
        );
      case "loading":
      case "rows":
        return (
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
        );
    }
  };

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
          {renderCustomRows(renderRows)}
        </section>
      ) : null}
      {!renderRows && tablePresentation !== false ? (
        <div className={presentationClass(tablePresentation)}>
          <Table
            aria-label={t("tableList.view", { collection: collectionLabel })}
            aria-busy={status === "loading" || status === "refreshing"}
            className="table-fixed min-w-full"
            style={{ width: table.getTotalSize() }}
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
            <TableBody>{renderTableBody()}</TableBody>
          </Table>
        </div>
      ) : null}
      {!renderRows && cardPresentation !== false ? (
        <div className={presentationClass(cardPresentation)}>{renderCards()}</div>
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
