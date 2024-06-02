//@flow strict

/*
 * These are some typed definitions for the MUI component library. These are
 * just added when it is deemed worth it, rather than trying to accurately type
 * each and every component.
 */

declare module "@mui/styled-engine/StyledEngineProvider" {
  import type { ComponentType, Node } from "react";

  declare export default ComponentType<{|
    injectFirst: boolean,
    children: Node,
  |}>;
}

declare module "@mui/system" {
  /**
   * Darkens a color.
   * @param {string} color - CSS color, i.e. one of: #nnn, #nnnnnn, rgb(), rgba(), hsl(), hsla(), color()
   * @param {number} coefficient - multiplier in the range 0 - 1
   * @returns {string} A CSS color string. Hex input values are returned as rgb
   */
  declare export function darken(color: string, coefficient: number): string;
}

declare module "@mui/x-data-grid" {
  import type { ComponentType, Node, Ref } from "react";

  declare export type Column<Row> = {|
    headerName: string,
    field: string,
    valueGetter?: (params: { row: Row, ... }) => mixed,
    renderCell?: (params: {
      row: Row,
      value: mixed,
      tabIndex: number,
      ...
    }) => Node,
    valueFormatter?: ({ value: mixed, ... }) => Node,
    hideable?: boolean,
    width?: number,
    flex?: number,
    disableColumnMenu?: boolean,
    sortable?: boolean,
    headerClassName?: string,
    disableExport?: boolean,
  |};

  declare export function DataGrid<
    Row,
    ColumnNames: string,
    Id: mixed,
    ToolbarProps: { ... },
    Value: mixed
  >({|
    rows: $ReadOnlyArray<Row>,
    columns: $ReadOnlyArray<Column<Row>>,
    initialState?: {|
      columns?: {|
        columnVisibilityModel?: { [ColumnNames]: boolean },
      |},
    |},
    disableColumnFilter?: boolean,
    density?: "compact" | "standard" | "comfortable",
    getRowId?: (Row) => Id,
    rowSelectionModel?: $ReadOnlyArray<Id>,
    onRowSelectionModelChange?: ($ReadOnlyArray<Id>) => void,
    hideFooterSelectedRowCount?: boolean,
    paginationMode?: "server" | "client",
    rowCount?: number,
    paginationModel?: {| pageSize: number, page: number |},
    pageSizeOptions?: $ReadOnlyArray<
      number | {| value: number, label: string |}
    >,
    onPaginationModelChange?: ({| pageSize: number, page: number |}) => void,
    sortingMode?: "server",
    sortModel?: Array<{| field: string, sort: "asc" | "desc" |}>,
    onSortModelChange?: (
      Array<{| field: string, sort: "asc" | "desc" |}>
    ) => void,
    slots?: {|
      toolbar?: (ToolbarProps) => Node,
    |},
    className?: string,
    classes?: {||},
    "aria-hidden"?: boolean,
    "aria-label"?: string,
    autoHeight?: boolean,
    loading?: boolean,
    checkboxSelection?: boolean,
    componentsProps?: {|
      toolbar?: ToolbarProps,
      panel?: { ... },
    |},
  |}): Node;

  declare export function GridToolbarContainer({|
    children: Node,
  |}): Node;

  declare export function GridToolbarColumnsButton({|
    variant?: "outlined",
    onClick?: (Event & { target: EventTarget, ... }) => void,
  |}): Node;

  declare export function GridToolbarDensitySelector({|
    variant?: "outlined",
  |}): Node;

  declare export function GridToolbarExport({|
    variant?: "outlined",
  |}): Node;

  declare export function GridToolbarExportContainer({|
    variant?: "outlined",
    children: Node,
  |}): Node;

  declare export function GridCsvExportMenuItem({|
    options?: {|
      allColumns?: boolean,
    |},
  |}): Node;

  declare export function useGridApiContext(): {|
    current: ?{|
      exportDataAsCsv: ({|
        getRowsToExport?: () => Array<mixed>,
        allColumns?: boolean,
      |}) => void,
    |},
  |};
}
