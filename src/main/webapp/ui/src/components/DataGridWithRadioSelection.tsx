import React from "react";
import {
  DataGrid,
  GRID_CHECKBOX_SELECTION_COL_DEF,
  GridColDef,
  GridRenderCellParams,
  GridRowId,
  useGridApiRef,
} from "@mui/x-data-grid";
import { Radio } from "@mui/material";

/**
 * A DataGrid, where the first column is a Radio field for selecting a single
 * column. MUI's DataGrid supports checkbox-based selection by setting the
 * `checkboxSelection` prop and whilst the `disableMultipleRowSelection` does
 * what it suggests it does not change the checkboxes to radio buttons, which is
 * the more semantic UI element for singular selection.
 */
export function DataGridWithRadioSelection({
  columns,
  onSelectionChange,
  localeText,
  selectRadioAriaLabelFunc,
  ...props
}: {
  /*
   * The list of other columns to which the selection column with be prepended.
   */
  columns: GridColDef[];

  /*
   * When the selection changes, this function will be called.
   */
  onSelectionChange: (selectedId: GridRowId) => void;

  /*
   * Each of the radio buttons should have an aria-label that describes what the
   * UI element is for to those using screen readers. This function allows
   * callers to specify a label that is specific to each row e.g. "Select Foo"
   */
  selectRadioAriaLabelFunc: (col: GridRenderCellParams["row"]) => string;
} & Omit<
  React.ComponentProps<typeof DataGrid>,
  | "columns"
  | "checkboxSelection"
  | "disableMultipleRowSelection"
  | "rowSelectionModel"
  | "onRowSelectionModelChange"
>) {
  const apiRef = useGridApiRef();
  const [selectedRowId, setSelectedRowId] = React.useState<GridRowId | null>(
    null
  );

  const radioSelectionColumn: GridColDef = React.useMemo(
    () => ({
      ...GRID_CHECKBOX_SELECTION_COL_DEF,
      renderHeader: () => "Select",
      renderCell: (params: GridRenderCellParams) => {
        const isSelected = selectedRowId === params.id;

        return (
          <Radio
            color="primary"
            checked={isSelected}
            onChange={(event: unknown, selected: boolean | null) => {
              if (selected === null) {
                setSelectedRowId(null);
                return;
              }
              setSelectedRowId(params.id);
              onSelectionChange(params.id);
            }}
            aria-label={selectRadioAriaLabelFunc(params.row)}
            onClick={(e) => e.stopPropagation()} // Prevent row selection on click
          />
        );
      },
      width: 70,
      flex: 0,
      hideable: false,
      disableMenuColumn: true,
    }),
    [onSelectionChange, selectedRowId, selectRadioAriaLabelFunc]
  );

  const allColumns = React.useMemo(() => {
    return [radioSelectionColumn, ...columns];
  }, [radioSelectionColumn, columns]);

  return (
    <DataGrid
      apiRef={apiRef}
      columns={allColumns}
      checkboxSelection
      disableMultipleRowSelection
      rowSelectionModel={selectedRowId === null ? [] : [selectedRowId]}
      localeText={{
        checkboxSelectionHeaderName: "Select",
        ...(localeText ?? {}),
      }}
      {...props}
    />
  );
}
