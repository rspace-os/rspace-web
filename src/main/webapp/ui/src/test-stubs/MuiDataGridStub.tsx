import React from "react";

type GridId = string | number;

type SelectionModel =
  | {
      type?: "include" | "exclude";
      ids?: Set<GridId> | Array<GridId>;
    }
  | Array<GridId>
  | undefined;

type GridColumn<Row> = {
  field?: string;
  headerName?: string;
  hide?: boolean;
  disableExport?: boolean;
  renderHeader?: () => React.ReactNode;
  renderCell?: (params: {
    id: GridId;
    field: string;
    row: Row;
    value: unknown;
  }) => React.ReactNode;
  valueGetter?: (...args: Array<unknown>) => unknown;
  valueFormatter?: (value: unknown) => unknown;
};

type DataGridStubProps<Row> = {
  rows?: Array<Row>;
  columns?: Array<GridColumn<Row>>;
  getRowId?: (row: Row) => GridId;
  checkboxSelection?: boolean;
  rowSelectionModel?: SelectionModel;
  onRowSelectionModelChange?: (model: {
    type: "include";
    ids: Set<GridId>;
  }) => void;
  columnVisibilityModel?: Record<string, boolean>;
  localeText?: {
    checkboxSelectionHeaderName?: string;
    noRowsLabel?: string;
  };
  initialState?: {
    columns?: {
      columnVisibilityModel?: Record<string, boolean>;
    };
  };
  showToolbar?: boolean;
  slots?: {
    toolbar?: React.ComponentType<Record<string, unknown>>;
  };
  slotProps?: {
    toolbar?: Record<string, unknown>;
  };
  apiRef?: React.MutableRefObject<{ exportDataAsCsv: () => void } | null>;
  ["aria-label"]?: string;
};

const GridApiContext = React.createContext<React.MutableRefObject<{
  exportDataAsCsv: () => void;
} | null> | null>(null);

const toSelectedIds = (selectionModel: SelectionModel): Set<GridId> => {
  if (Array.isArray(selectionModel)) return new Set(selectionModel);
  const ids = selectionModel?.ids;
  if (!ids) return new Set();
  return ids instanceof Set ? new Set(ids) : new Set(ids);
};

const getColumnValue = <Row,>(row: Row, column: GridColumn<Row>): unknown => {
  const baseValue =
    typeof column.valueGetter === "function"
      ? (() => {
          const fieldValue =
            typeof column.field === "string" && row && typeof row === "object"
              ? (row as Record<string, unknown>)[column.field]
              : undefined;
          return column.valueGetter?.(fieldValue, row);
        })()
      : typeof column.field === "string" && row && typeof row === "object"
        ? (row as Record<string, unknown>)[column.field]
        : undefined;
  if (typeof column.valueFormatter === "function") {
    return column.valueFormatter(baseValue);
  }
  if (typeof column.valueGetter === "function") {
    return baseValue;
  }
  return baseValue;
};

const toText = (value: unknown): string => {
  if (value === null || value === undefined || typeof value === "boolean") {
    return "";
  }
  return String(value);
};

const escapeCsv = (value: string): string => {
  if (/[",\n]/.test(value)) return `"${value.replaceAll('"', '""')}"`;
  return value;
};

export const gridClasses = new Proxy(
  {},
  {
    get: (_target, property) => `MuiDataGrid-${String(property)}`,
  },
) as Record<string, string>;

export const GRID_CHECKBOX_SELECTION_COL_DEF = {
  field: "__checkbox__",
};

export const useGridApiRef = () =>
  React.useRef<{ exportDataAsCsv: () => void }>({
    exportDataAsCsv: () => {},
  });

export const useGridApiContext = () => {
  const apiRef = React.useContext(GridApiContext);
  return (
    apiRef ??
    ({
      current: {
        exportDataAsCsv: () => {},
      },
    } as React.MutableRefObject<{ exportDataAsCsv: () => void }>)
  );
};

export const GridToolbarContainer = ({
  children,
}: {
  children: React.ReactNode;
}) => <div role="toolbar">{children}</div>;

const ColumnsMenuContext = React.createContext<{
  columns: Array<{ label: string }>;
} | null>(null);

export const GridToolbarColumnsButton = () => {
  const columnsMenu = React.useContext(ColumnsMenuContext);
  const [open, setOpen] = React.useState(false);

  return (
    <div>
      <button type="button" onClick={() => setOpen((current) => !current)}>
        Select columns
      </button>
      {open ? (
        <div role="menu">
          <label>
            <input type="checkbox" aria-label="Show/Hide All" defaultChecked />
            Show/Hide All
          </label>
          <label>
            <input
              type="checkbox"
              aria-label="Checkbox selection"
              defaultChecked
            />
            Checkbox selection
          </label>
          {(columnsMenu?.columns ?? []).map(({ label }) => (
            <label key={label}>
              <input type="checkbox" aria-label={label} defaultChecked />
              {label}
            </label>
          ))}
        </div>
      ) : null}
    </div>
  );
};

export const ColumnsPanelTrigger = GridToolbarColumnsButton;

export const GridToolbarExportContainer = ({
  children,
}: {
  children: React.ReactNode;
}) => {
  const [open, setOpen] = React.useState(false);

  return (
    <div>
      <button type="button" onClick={() => setOpen((current) => !current)}>
        Export
      </button>
      {open ? (
        <div role="menu">
          {React.Children.map(children, (child) =>
            React.isValidElement(child)
              ? React.cloneElement(
                  child as React.ReactElement<{ hideMenu?: () => void }>,
                  { hideMenu: () => setOpen(false) },
                )
              : child,
          )}
        </div>
      ) : null}
    </div>
  );
};

export const Toolbar = GridToolbarContainer;

export function DataGrid<Row>({
  rows = [],
  columns = [],
  getRowId,
  checkboxSelection = false,
  rowSelectionModel,
  onRowSelectionModelChange,
  columnVisibilityModel,
  localeText,
  initialState,
  showToolbar,
  slots,
  slotProps,
  apiRef,
  "aria-label": ariaLabel,
}: DataGridStubProps<Row>) {
  const internalApiRef =
    apiRef ??
    ({
      current: {
        exportDataAsCsv: () => {},
      },
    } as React.MutableRefObject<{ exportDataAsCsv: () => void }>);

  const selectedIds = toSelectedIds(rowSelectionModel);
  const effectiveColumnVisibility = {
    ...(initialState?.columns?.columnVisibilityModel ?? {}),
    ...(columnVisibilityModel ?? {}),
  };
  const visibleColumns = columns.filter((column) => {
    if (!column.field) return true;
    return (
      effectiveColumnVisibility[column.field] !== false && column.hide !== true
    );
  });
  const allColumns = columns.filter(
    (column) => column.field !== "__checkbox__" && !column.disableExport,
  );
  const ToolbarComponent = showToolbar ? slots?.toolbar : undefined;

  internalApiRef.current = {
    exportDataAsCsv: (options?: {
      getRowsToExport?: () => Array<GridId>;
      allColumns?: boolean;
    }) => {
      const selectedRowIds = toSelectedIds(rowSelectionModel);
      const rowIdsToExport =
        options?.getRowsToExport?.() ??
        (selectedRowIds.size > 0
          ? Array.from(selectedRowIds)
          : rows.map((row, rowIndex) => (getRowId ? getRowId(row) : rowIndex)));
      const columnsToExport = (options?.allColumns ? allColumns : visibleColumns)
        .filter((column) => column.field)
        .map((column) => ({
          ...column,
          field: column.field as string,
        }));
      const exportedRows = rows.filter((row, rowIndex) =>
        rowIdsToExport.includes(getRowId ? getRowId(row) : rowIndex),
      );
      const lines = [
        columnsToExport
          .map((column) => escapeCsv(column.headerName ?? column.field))
          .join(","),
        ...exportedRows.map((row) =>
          columnsToExport
            .map((column) => escapeCsv(toText(getColumnValue(row, column))))
            .join(","),
        ),
      ];
      const blob = new Blob([lines.join("\n")], { type: "text/csv" });
      window.URL.createObjectURL(blob);
    },
  };

  const toggleRow = (id: GridId, checked: boolean) => {
    const nextIds = new Set(selectedIds);
    if (checked) nextIds.add(id);
    else nextIds.delete(id);
    onRowSelectionModelChange?.({
      type: "include",
      ids: nextIds,
    });
  };

  return (
    <GridApiContext.Provider value={internalApiRef}>
      <ColumnsMenuContext.Provider
        value={{
          columns: allColumns
            .filter((column) => column.field)
            .map((column) => ({
              label: column.headerName ?? column.field ?? "",
            })),
        }}
      >
        <div>
          {ToolbarComponent ? <ToolbarComponent {...slotProps?.toolbar} /> : null}
        <div role="grid" aria-label={ariaLabel}>
          <div role="row">
            {checkboxSelection ? (
              <div role="columnheader">
                {localeText?.checkboxSelectionHeaderName ?? "Select"}
              </div>
            ) : null}
            {visibleColumns.map((column, index) => (
              <div
                role="columnheader"
                key={column.field ?? column.headerName ?? index}
              >
                {typeof column.renderHeader === "function"
                  ? column.renderHeader()
                  : column.headerName ?? column.field ?? ""}
              </div>
            ))}
          </div>
          {rows.length === 0 ? (
            <div role="row">
              <div role="gridcell">
                {localeText?.noRowsLabel ?? "No rows"}
              </div>
            </div>
          ) : null}
          {rows.map((row, rowIndex) => {
            const id = getRowId ? getRowId(row) : rowIndex;
            return (
              <div role="row" key={String(id)}>
                {checkboxSelection ? (
                  <div role="gridcell">
                    <input
                      type="checkbox"
                      role="checkbox"
                      aria-label="Select row"
                      checked={selectedIds.has(id)}
                      onChange={(event) => toggleRow(id, event.target.checked)}
                    />
                  </div>
                ) : null}
                {visibleColumns.map((column, columnIndex) => {
                  const value = getColumnValue(row, column);
                  const content =
                    typeof column.renderCell === "function"
                      ? column.renderCell({
                          id,
                          field: column.field ?? String(columnIndex),
                          row,
                          value,
                        })
                      : toText(value);
                  return (
                    <div
                      role="gridcell"
                      key={`${String(id)}-${column.field ?? columnIndex}`}
                    >
                      {content}
                    </div>
                  );
                })}
              </div>
            );
          })}
        </div>
        </div>
      </ColumnsMenuContext.Provider>
    </GridApiContext.Provider>
  );
}

export const DataGridProps = {};
export const GridColDef = {};
export const GridColumnVisibilityModel = {};
export const GridRenderCellParams = {};
export const GridRowId = {};
export const GridRowSelectionModel = {};
export const GridSlotProps = {};
export const GridSortModel = {};
export const GridValidRowModel = {};
