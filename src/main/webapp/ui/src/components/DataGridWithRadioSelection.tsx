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
 * A DataGrid, where the first column is a Radio field for selecting a single column
 */
export function DataGridWithRadioSelection({
  columns,
  onSelectionChange,
  localeText,
  selectRadioAriaLabelFunc,
  ...props
}: {
  columns: GridColDef[];
  onSelectionChange: (selectedId: GridRowId) => void;
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
