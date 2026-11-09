import {
  type ColumnOrderState,
  type ColumnSizingState,
  type ColumnVisibilityState,
  FlexRender,
  functionalUpdate,
  type SortingState,
  useTable,
} from "@tanstack/react-table";
import { clamp } from "es-toolkit";
import type { Namespace } from "i18next";
import { Fragment, type ReactNode, useCallback, useId, useMemo, useRef, useState } from "react";
import { flushSync } from "react-dom";
import { useTranslation } from "react-i18next";
import type { FieldName } from "@/modules/common/collection/config";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/modules/common/ui/table";
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
  | "stickyActions"
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
  stickyActions = "always",
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
  const { i18n, t } = useTranslation<Namespace>("common");
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

  // React owns the column sizes: the sizes captured at the first resize plus the user's edits.
  const [storedSizing, setStoredSizing] = useState<ColumnSizingState>({});
  const [capturedLayout, setCapturedLayout] = useState(false);
  const [userSizedFillId, setUserSizedFillId] = useState<string | null>(null);
  // A fill resize that starts the capture works from the measured fill width until it ends.
  const [capturingFillId, setCapturingFillId] = useState<string | null>(null);
  // Written and read only in resize handlers.
  const pendingFillResize = useRef<{ id: string; size: number } | null>(null);
  const [containerWidth, setContainerWidth] = useState(0);
  const observeContainer = useCallback((container: HTMLDivElement) => {
    const observer = new ResizeObserver(() => setContainerWidth(container.clientWidth));
    observer.observe(container);
    setContainerWidth(container.clientWidth);
    return () => observer.disconnect();
  }, []);
  const [overflowing, setOverflowing] = useState(false);
  const observeTable = useCallback((element: HTMLTableElement) => {
    const scroller = element.parentElement;
    if (!scroller) return;
    const updateOverflow = () => setOverflowing(scroller.scrollWidth > scroller.clientWidth);
    const observer = new ResizeObserver(updateOverflow);
    observer.observe(element);
    observer.observe(scroller);
    updateOverflow();
    return () => observer.disconnect();
  }, []);
  // Once captured, an untouched fill column takes the container's spare width, so refitting follows the Columns
  // panel, saved-view restores, navigation and container changes. A fill width the user chose stays until reset.
  const visibleColumnDefs = [
    ...columnOrder.flatMap((id) => columns.filter((column) => column.id === id)),
    ...columns.filter((column) => column.id === undefined || !columnOrder.includes(column.id)),
  ].filter((column) => column.id !== undefined && columnVisibility[column.id] !== false);
  const sizeOf = (column: (typeof columns)[number]) =>
    clamp(
      (column.id === undefined ? undefined : storedSizing[column.id]) ?? column.size ?? 150,
      column.minSize ?? 20,
      column.maxSize ?? Number.MAX_SAFE_INTEGER,
    );
  const fillDef = visibleColumnDefs.find((column) => column.meta?.widthMode === "fill");
  const effectiveSizing: ColumnSizingState =
    capturedLayout && fillDef?.id !== undefined && userSizedFillId !== fillDef.id && capturingFillId !== fillDef.id
      ? {
          ...storedSizing,
          [fillDef.id]: clamp(
            containerWidth -
              visibleColumnDefs.filter((column) => column !== fillDef).reduce((sum, column) => sum + sizeOf(column), 0),
            fillDef.minSize ?? 20,
            fillDef.maxSize ?? Number.MAX_SAFE_INTEGER,
          ),
        }
      : storedSizing;

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
      columnSizing: effectiveSizing,
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
    onColumnSizingChange: (updater) => {
      const next = functionalUpdate(updater, effectiveSizing);
      const pending = pendingFillResize.current;
      if (pending && Math.abs((next[pending.id] ?? pending.size) - pending.size) >= 0.01) {
        setUserSizedFillId(pending.id);
        pendingFillResize.current = null;
      } else if (userSizedFillId !== null && next[userSizedFillId] === undefined) {
        // A reset dropped the user's fill width.
        setUserSizedFillId(null);
      }
      setCapturingFillId(null);
      setStoredSizing(next);
    },
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
  const { visibleRows, visibleRowIds } = useMemo(
    () => ({
      visibleRows: rowModel.rows.map((row) => row.original),
      visibleRowIds: rowModel.rows.map((row) => row.id),
    }),
    [rowModel],
  );
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

  const visibleColumns = table.getVisibleLeafColumns();
  const hasWidthModes = columns.some((column) => column.meta?.widthMode);
  const fillColumn = visibleColumns.find((column) => column.columnDef.meta?.widthMode === "fill");
  const totalWidth = table.getTotalSize();
  const pinActions = stickyActions === "always" || !overflowing;

  // Capture by identity: selection and presentation cells need not have resize handles.
  const prepareResize = (element: HTMLElement) => {
    const resizingFill = fillColumn && element.closest("th")?.dataset.columnId === fillColumn.id;
    if (!hasWidthModes || capturedLayout) {
      pendingFillResize.current = resizingFill ? { id: fillColumn.id, size: fillColumn.getSize() } : null;
      return true;
    }
    const headers = new Map(
      Array.from(element.closest("tr")?.querySelectorAll<HTMLTableCellElement>("th[data-column-id]") ?? []).map(
        (header) => [header.dataset.columnId, header],
      ),
    );
    const captured: [string, number][] = [];
    for (const column of visibleColumns) {
      const header = headers.get(column.id);
      if (!header) {
        console.warn(`Missing rendered header for column ${column.id}; cancelling column resize`);
        pendingFillResize.current = null;
        return false;
      }
      captured.push([
        column.id,
        Math.min(
          column.columnDef.maxSize ?? Number.MAX_SAFE_INTEGER,
          Math.max(column.columnDef.minSize ?? 20, header.getBoundingClientRect().width),
        ),
      ]);
    }
    const sizes = Object.fromEntries(captured);
    // Commit the capture before the resize handler that follows reads the column sizes.
    flushSync(() => {
      setStoredSizing(sizes);
      setCapturedLayout(true);
      setCapturingFillId(resizingFill ? fillColumn.id : null);
    });
    pendingFillResize.current = resizingFill ? { id: fillColumn.id, size: fillColumn.getSize() } : null;
    return true;
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

  const spacerBeforeId = visibleColumns.some((column) => column.id === "actions")
    ? "actions"
    : visibleColumns.at(-1)?.id;
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
                <Fragment key={cell.id}>
                  {capturedLayout && cell.column.id === spacerBeforeId ? (
                    <TableCell role="presentation" className="p-0" />
                  ) : null}
                  <TableCell
                    className={cn(
                      "overflow-hidden py-2 text-ellipsis",
                      cell.column.id === "actions" &&
                        pinActions &&
                        "sticky right-0 z-10 bg-background group-hover/row:bg-[color-mix(in_oklab,var(--color-muted)_40%,var(--color-background))] group-data-[state=selected]/row:bg-[color-mix(in_oklab,var(--color-primary)_10%,var(--color-background))]",
                    )}
                  >
                    {cell.column.id === selectionColumnId && selection ? (
                      renderRowSelection(row.id, row.original)
                    ) : (
                      <FlexRender cell={cell} />
                    )}
                  </TableCell>
                </Fragment>
              ))}
            </TableRow>
          )),
    [
      capturedLayout,
      pinActions,
      spacerBeforeId,
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
      const footerCells = cells.filter((cell) => cell.column.columnDef.meta?.item?.placement === "footer");
      const fields = cells
        .filter(
          (cell) =>
            cell.column.id !== selectionColumnId &&
            cell.column.id !== config.useAsTitle &&
            cell.column.columnDef.meta?.item?.placement !== "footer",
        )
        .map((cell) => ({
          id: cell.column.id,
          label: cell.column.columnDef.meta?.item?.label ?? cell.column.id,
          value: <FlexRender cell={cell} />,
          fullWidth: cell.column.columnDef.meta?.item?.fullWidth,
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
  const intrinsicWidths = hasWidthModes && !capturedLayout;
  const resetColumn = (columnId: string) => {
    if (hasWidthModes || capturedLayout) {
      table.resetColumnSizing(true);
      setCapturedLayout(false);
      setUserSizedFillId(null);
      pendingFillResize.current = null;
    } else {
      table.getColumn(columnId)?.resetSize();
    }
  };
  const colSpan = Math.max(visibleColumns.length + (capturedLayout ? 1 : 0), 1);
  const columnResizing = table.atoms.columnResizing.get();

  const renderBodyState = (
    state: BodyState,
    view: {
      loading: () => ReactNode;
      rows: () => ReactNode;
      message: (notice: ReactNode, state: "error" | "empty") => ReactNode;
    },
  ): ReactNode => {
    switch (state) {
      case "loading":
        return view.loading();
      case "rows":
        return view.rows();
      case "error":
        return view.message(<TableListErrorState error={error} />, "error");
      case "empty":
        return view.message(<TableListEmptyState description={emptyDescription} />, "empty");
    }
  };

  const renderCustomRows = (render: (rows: readonly TDocument[]) => ReactNode): ReactNode =>
    renderBodyState(bodyStateFor(status, visibleRows.length, renderRowsWhenEmpty), {
      loading: () => (
        <div className="space-y-3 py-3">
          {Array.from({ length: 4 }, (_, index) => (
            <Skeleton key={index} className="h-12 w-full rounded-sm" />
          ))}
        </div>
      ),
      rows: () => render(visibleRows),
      message: (notice) => notice,
    });

  const renderTableBody = (): ReactNode =>
    renderBodyState(bodyStateFor(status, rowModel.rows.length, false), {
      loading: () =>
        Array.from({ length: 5 }, (_, index) => (
          <TableRow key={index}>
            <TableCell colSpan={colSpan}>
              <Skeleton className="h-5 w-full rounded-sm" />
            </TableCell>
          </TableRow>
        )),
      rows: () => renderedDataRows,
      message: (notice, state) => (
        <TableRow>
          <TableCell colSpan={colSpan} className={state === "empty" && reserveEmptyRows ? "h-90" : "h-40"}>
            {notice}
          </TableCell>
        </TableRow>
      ),
    });

  const renderCards = (): ReactNode =>
    renderBodyState(bodyStateFor(status, rowModel.rows.length, false), {
      loading: () => (
        <section aria-label={cardViewLabel} aria-busy="true" data-slot="table-list-card-view" className="py-3">
          <div aria-hidden="true" className="grid grid-cols-1 gap-3 @3xl/table-list:grid-cols-2">
            {Array.from({ length: 4 }, (_, index) => (
              <Skeleton key={index} className="h-28 w-full rounded-md" />
            ))}
          </div>
        </section>
      ),
      rows: () => (
        <TableListCardGrid
          label={cardViewLabel}
          items={renderedCardItems}
          busy={status === "refreshing"}
          selectionControl={
            selection ? (
              <>
                {selectAllPage}
                <span aria-hidden="true">{t("tableList.selection.selectAllPage")}</span>
              </>
            ) : undefined
          }
        />
      ),
      message: (notice) => (
        <section aria-label={cardViewLabel} className={reserveEmptyRows ? "min-h-90 py-3" : "min-h-40 py-3"}>
          {notice}
        </section>
      ),
    });

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
        <div
          ref={observeContainer}
          className={cn(presentationClass(tablePresentation), "[&>[data-slot=table-container]]:scroll-pr-3")}
        >
          <Table
            ref={observeTable}
            aria-label={t("tableList.view", { collection: collectionLabel })}
            aria-busy={status === "loading" || status === "refreshing"}
            className={cn(intrinsicWidths ? "table-auto" : "table-fixed", "min-w-full")}
            style={{
              width: intrinsicWidths ? "100%" : totalWidth,
            }}
          >
            <colgroup>
              {visibleColumns.map((column) => (
                <Fragment key={column.id}>
                  {capturedLayout && column.id === spacerBeforeId ? <col style={{ width: "auto" }} /> : null}
                  <col
                    style={{
                      width:
                        intrinsicWidths && column.columnDef.meta?.widthMode === "fill"
                          ? "100%"
                          : intrinsicWidths && column.columnDef.meta?.widthMode === "content"
                            ? "1%"
                            : column.getSize(),
                    }}
                  />
                </Fragment>
              ))}
            </colgroup>
            <TableHeader>
              {table.getHeaderGroups().map((group) => (
                <TableRow key={group.id} className="bg-muted/35 hover:bg-muted/35">
                  {group.headers.map((header) => (
                    <Fragment key={header.id}>
                      {capturedLayout && header.column.id === spacerBeforeId ? (
                        <TableHead role="presentation" className="p-0" />
                      ) : null}
                      <TableListHeaderCell
                        header={header}
                        pinActions={pinActions}
                        resizeId={resizeId}
                        resizeOffset={
                          columnResizing.isResizingColumn === header.column.id ? (columnResizing.deltaOffset ?? 0) : 0
                        }
                        selectAllControl={selection ? selectAllPage : undefined}
                        onResize={resizeColumn}
                        onResizeStart={prepareResize}
                        onReset={resetColumn}
                      />
                    </Fragment>
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
