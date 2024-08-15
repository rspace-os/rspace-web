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

  /**
   * Lightens a color.
   * @param {string} color - CSS color, i.e. one of: #nnn, #nnnnnn, rgb(), rgba(), hsl(), hsla(), color()
   * @param {number} coefficient - multiplier in the range 0 - 1
   * @returns {string} A CSS color string. Hex input values are returned as rgb
   */
  declare export function lighten(color: string, coefficient: number): string;

  /**
   * Applies a transparency to a color.
   * @param {string} color - CSS color, i.e. one of: #nnn, #nnnnnn, rgb(), rgba(), hsl(), hsla(), color()
   * @param {number} opacity - number in the range 0 - 1
   * @returns {string} A CSS color string. Hex input values are returned as rgb
   */
  declare export function alpha(color: string, opacity: number): string;
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
    display?: "text" | "flex",
  |};

  declare export function DataGrid<
    Row,
    ColumnNames: string,
    Id: mixed,
    ToolbarProps: { ... },
    Value: mixed,
    SortableColumnNames: ColumnNames,
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
    getRowHeight?: () => "auto" | number | null,
    rowSelectionModel?: $ReadOnlyArray<Id>,
    onRowSelectionModelChange?: ($ReadOnlyArray<Id>) => void,
    hideFooterSelectedRowCount?: boolean,
    hideFooter?: boolean,
    paginationMode?: "server" | "client",
    rowCount?: number,
    paginationModel?: {| pageSize: number, page: number |},
    pageSizeOptions?: $ReadOnlyArray<
      number | {| value: number, label: string |}>,
    onPaginationModelChange?: ({| pageSize: number, page: number |}) => void,
    sortingMode?: "server",
    sortModel?: $ReadOnlyArray<{| field: SortableColumnNames, sort: "asc" | "desc" |}>,
    onSortModelChange?: (
      $ReadOnlyArray<{| field: SortableColumnNames, sort: "asc" | "desc" |}>
    ) => void,
    slots?: {|
      toolbar?: (ToolbarProps) => Node,
      pagination?: null,
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
    localeText?: {|
      // https://github.com/mui/mui-x/blob/v7.12.0/packages/x-data-grid/src/constants/localeTextConstants.ts
      noRowsLabel?: string,
    |},
    onCellKeyDown?: (Row, KeyboardEvent) => void,
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

  declare export const gridClasses: {|
    "actionsCell": "MuiDataGrid-actionsCell",
    "aggregationColumnHeader": "MuiDataGrid-aggregationColumnHeader",
    "aggregationColumnHeader--alignLeft": "MuiDataGrid-aggregationColumnHeader--alignLeft",
    "aggregationColumnHeader--alignCenter": "MuiDataGrid-aggregationColumnHeader--alignCenter",
    "aggregationColumnHeader--alignRight": "MuiDataGrid-aggregationColumnHeader--alignRight",
    "aggregationColumnHeaderLabel": "MuiDataGrid-aggregationColumnHeaderLabel",
    "autoHeight": "MuiDataGrid-autoHeight",
    "autosizing": "MuiDataGrid-autosizing",
    "booleanCell": "MuiDataGrid-booleanCell",
    "cell--editable": "MuiDataGrid-cell--editable",
    "cell--editing": "MuiDataGrid-cell--editing",
    "cell--textCenter": "MuiDataGrid-cell--textCenter",
    "cell--textLeft": "MuiDataGrid-cell--textLeft",
    "cell--textRight": "MuiDataGrid-cell--textRight",
    "cell--withRenderer": "MuiDataGrid-cell--withRenderer",
    "cell--rangeTop": "MuiDataGrid-cell--rangeTop",
    "cell--rangeBottom": "MuiDataGrid-cell--rangeBottom",
    "cell--rangeLeft": "MuiDataGrid-cell--rangeLeft",
    "cell--rangeRight": "MuiDataGrid-cell--rangeRight",
    "cell--selectionMode": "MuiDataGrid-cell--selectionMode",
    "cell": "MuiDataGrid-cell",
    "cellContent": "MuiDataGrid-cellContent",
    "cellCheckbox": "MuiDataGrid-cellCheckbox",
    "cellSkeleton": "MuiDataGrid-cellSkeleton",
    "checkboxInput": "MuiDataGrid-checkboxInput",
    "columnHeader--alignCenter": "MuiDataGrid-columnHeader--alignCenter",
    "columnHeader--alignLeft": "MuiDataGrid-columnHeader--alignLeft",
    "columnHeader--alignRight": "MuiDataGrid-columnHeader--alignRight",
    "columnHeader--dragging": "MuiDataGrid-columnHeader--dragging",
    "columnHeader--moving": "MuiDataGrid-columnHeader--moving",
    "columnHeader--numeric": "MuiDataGrid-columnHeader--numeric",
    "columnHeader--sortable": "MuiDataGrid-columnHeader--sortable",
    "columnHeader--sorted": "MuiDataGrid-columnHeader--sorted",
    "columnHeader--filtered": "MuiDataGrid-columnHeader--filtered",
    "columnHeader": "MuiDataGrid-columnHeader",
    "columnHeaderCheckbox": "MuiDataGrid-columnHeaderCheckbox",
    "columnHeaderDraggableContainer": "MuiDataGrid-columnHeaderDraggableContainer",
    "columnHeaderDropZone": "MuiDataGrid-columnHeaderDropZone",
    "columnHeaderTitle": "MuiDataGrid-columnHeaderTitle",
    "columnHeaderTitleContainer": "MuiDataGrid-columnHeaderTitleContainer",
    "columnHeaderTitleContainerContent": "MuiDataGrid-columnHeaderTitleContainerContent",
    "columnGroupHeader": "MuiDataGrid-columnGroupHeader",
    "columnHeader--filledGroup": "MuiDataGrid-columnHeader--filledGroup",
    "columnHeader--emptyGroup": "MuiDataGrid-columnHeader--emptyGroup",
    "columnHeader--showColumnBorder": "MuiDataGrid-columnHeader--showColumnBorder",
    "columnHeaders": "MuiDataGrid-columnHeaders",
    "columnHeadersInner": "MuiDataGrid-columnHeadersInner",
    "columnHeadersInner--scrollable": "MuiDataGrid-columnHeadersInner--scrollable",
    "columnSeparator--resizable": "MuiDataGrid-columnSeparator--resizable",
    "columnSeparator--resizing": "MuiDataGrid-columnSeparator--resizing",
    "columnSeparator--sideLeft": "MuiDataGrid-columnSeparator--sideLeft",
    "columnSeparator--sideRight": "MuiDataGrid-columnSeparator--sideRight",
    "columnSeparator": "MuiDataGrid-columnSeparator",
    "columnsPanel": "MuiDataGrid-columnsPanel",
    "columnsPanelRow": "MuiDataGrid-columnsPanelRow",
    "detailPanel": "MuiDataGrid-detailPanel",
    "detailPanels": "MuiDataGrid-detailPanels",
    "detailPanelToggleCell": "MuiDataGrid-detailPanelToggleCell",
    "detailPanelToggleCell--expanded": "MuiDataGrid-detailPanelToggleCell--expanded",
    "footerCell": "MuiDataGrid-footerCell",
    "panel": "MuiDataGrid-panel",
    "panelHeader": "MuiDataGrid-panelHeader",
    "panelWrapper": "MuiDataGrid-panelWrapper",
    "panelContent": "MuiDataGrid-panelContent",
    "panelFooter": "MuiDataGrid-panelFooter",
    "paper": "MuiDataGrid-paper",
    "editBooleanCell": "MuiDataGrid-editBooleanCell",
    "editInputCell": "MuiDataGrid-editInputCell",
    "filterForm": "MuiDataGrid-filterForm",
    "filterFormDeleteIcon": "MuiDataGrid-filterFormDeleteIcon",
    "filterFormLogicOperatorInput": "MuiDataGrid-filterFormLogicOperatorInput",
    "filterFormColumnInput": "MuiDataGrid-filterFormColumnInput",
    "filterFormOperatorInput": "MuiDataGrid-filterFormOperatorInput",
    "filterFormValueInput": "MuiDataGrid-filterFormValueInput",
    "filterIcon": "MuiDataGrid-filterIcon",
    "footerContainer": "MuiDataGrid-footerContainer",
    "headerFilterRow": "MuiDataGrid-headerFilterRow",
    "iconButtonContainer": "MuiDataGrid-iconButtonContainer",
    "iconSeparator": "MuiDataGrid-iconSeparator",
    "main": "MuiDataGrid-main",
    "menu": "MuiDataGrid-menu",
    "menuIcon": "MuiDataGrid-menuIcon",
    "menuIconButton": "MuiDataGrid-menuIconButton",
    "menuOpen": "MuiDataGrid-menuOpen",
    "menuList": "MuiDataGrid-menuList",
    "overlay": "MuiDataGrid-overlay",
    "overlayWrapper": "MuiDataGrid-overlayWrapper",
    "overlayWrapperInner": "MuiDataGrid-overlayWrapperInner",
    "root": "MuiDataGrid-root",
    "root--densityStandard": "MuiDataGrid-root--densityStandard",
    "root--densityComfortable": "MuiDataGrid-root--densityComfortable",
    "root--densityCompact": "MuiDataGrid-root--densityCompact",
    "root--disableUserSelection": "MuiDataGrid-root--disableUserSelection",
    "row": "MuiDataGrid-row",
    "row--editable": "MuiDataGrid-row--editable",
    "row--editing": "MuiDataGrid-row--editing",
    "row--lastVisible": "MuiDataGrid-row--lastVisible",
    "row--dragging": "MuiDataGrid-row--dragging",
    "row--dynamicHeight": "MuiDataGrid-row--dynamicHeight",
    "row--detailPanelExpanded": "MuiDataGrid-row--detailPanelExpanded",
    "rowReorderCellPlaceholder": "MuiDataGrid-rowReorderCellPlaceholder",
    "rowCount": "MuiDataGrid-rowCount",
    "rowReorderCellContainer": "MuiDataGrid-rowReorderCellContainer",
    "rowReorderCell": "MuiDataGrid-rowReorderCell",
    "rowReorderCell--draggable": "MuiDataGrid-rowReorderCell--draggable",
    "scrollArea--left": "MuiDataGrid-scrollArea--left",
    "scrollArea--right": "MuiDataGrid-scrollArea--right",
    "scrollArea": "MuiDataGrid-scrollArea",
    "selectedRowCount": "MuiDataGrid-selectedRowCount",
    "sortIcon": "MuiDataGrid-sortIcon",
    "toolbarContainer": "MuiDataGrid-toolbarContainer",
    "toolbarFilterList": "MuiDataGrid-toolbarFilterList",
    "virtualScroller": "MuiDataGrid-virtualScroller",
    "virtualScrollerContent": "MuiDataGrid-virtualScrollerContent",
    "virtualScrollerContent--overflowed": "MuiDataGrid-virtualScrollerContent--overflowed",
    "virtualScrollerRenderZone": "MuiDataGrid-virtualScrollerRenderZone",
    "pinnedColumns": "MuiDataGrid-pinnedColumns",
    "pinnedColumns--left": "MuiDataGrid-pinnedColumns--left",
    "pinnedColumns--right": "MuiDataGrid-pinnedColumns--right",
    "pinnedColumnHeaders": "MuiDataGrid-pinnedColumnHeaders",
    "pinnedColumnHeaders--left": "MuiDataGrid-pinnedColumnHeaders--left",
    "pinnedColumnHeaders--right": "MuiDataGrid-pinnedColumnHeaders--right",
    "withBorderColor": "MuiDataGrid-withBorderColor",
    "cell--withRightBorder": "MuiDataGrid-cell--withRightBorder",
    "columnHeader--withRightBorder": "MuiDataGrid-columnHeader--withRightBorder",
    "treeDataGroupingCell": "MuiDataGrid-treeDataGroupingCell",
    "treeDataGroupingCellToggle": "MuiDataGrid-treeDataGroupingCellToggle",
    "groupingCriteriaCell": "MuiDataGrid-groupingCriteriaCell",
    "groupingCriteriaCellToggle": "MuiDataGrid-groupingCriteriaCellToggle",
    "pinnedRows": "MuiDataGrid-pinnedRows",
    "pinnedRows--top": "MuiDataGrid-pinnedRows--top",
    "pinnedRows--bottom": "MuiDataGrid-pinnedRows--bottom",
    "pinnedRowsRenderZone": "MuiDataGrid-pinnedRowsRenderZone"
  |};
}
