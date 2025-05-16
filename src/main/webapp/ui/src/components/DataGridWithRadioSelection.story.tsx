import React from "react";
import { DataGridWithRadioSelection } from "./DataGridWithRadioSelection";
import {
  GridColDef,
  GridRowId,
  GridToolbarContainer,
  GridToolbarExportContainer,
  useGridApiContext,
} from "@mui/x-data-grid";
import MenuItem from "@mui/material/MenuItem";

const rows = [
  { id: 1, firstName: "John", lastName: "Doe", age: 35 },
  { id: 2, firstName: "Jane", lastName: "Smith", age: 28 },
  { id: 3, firstName: "Bob", lastName: "Johnson", age: 42 },
  { id: 4, firstName: "Alice", lastName: "Williams", age: 31 },
  { id: 5, firstName: "Charlie", lastName: "Brown", age: 45 },
];

const columns: GridColDef[] = [
  { field: "firstName", headerName: "First Name", width: 150 },
  { field: "lastName", headerName: "Last Name", width: 150 },
  { field: "age", headerName: "Age", type: "number", width: 90 },
];

/**
 * Basic uncontrolled example of DataGridWithRadioSelection
 */
export function DataGridWithRadioSelectionExample() {
  const [lastSelectedId, setLastSelectedId] = React.useState<GridRowId | null>(
    null
  );

  const handleSelectionChange = (selectedId: GridRowId) => {
    setLastSelectedId(selectedId);
    console.log(`Row ${selectedId} selected`);
  };

  return (
    <div style={{ height: 400, width: "100%" }}>
      <DataGridWithRadioSelection
        rows={rows}
        columns={columns}
        onSelectionChange={handleSelectionChange}
        selectRadioAriaLabelFunc={(row) =>
          `Select ${row.firstName} ${row.lastName}`
        }
        data-testid="data-grid"
      />
      <div data-testid="selection-indicator">
        {lastSelectedId !== null
          ? `Selected ID: ${lastSelectedId}`
          : "Nothing selected"}
      </div>
    </div>
  );
}

/**
 * Controlled example of DataGridWithRadioSelection
 */
export function ControlledDataGridWithRadioSelectionExample({
  initialSelectedRowId = null,
  onSelectionChangeSpy = () => {},
}: {
  initialSelectedRowId?: GridRowId | null;
  onSelectionChangeSpy?: (selectedId: GridRowId) => void;
}) {
  const [selectedRowId, setSelectedRowId] = React.useState<GridRowId | null>(
    initialSelectedRowId
  );

  const handleSelectionChange = (selectedId: GridRowId) => {
    setSelectedRowId(selectedId);
    onSelectionChangeSpy(selectedId);
  };

  return (
    <div style={{ height: 400, width: "100%" }}>
      <DataGridWithRadioSelection
        rows={rows}
        columns={columns}
        onSelectionChange={handleSelectionChange}
        selectedRowId={selectedRowId}
        selectRadioAriaLabelFunc={(row) =>
          `Select ${row.firstName} ${row.lastName}`
        }
        data-testid="controlled-data-grid"
      />
      <div data-testid="selection-indicator">
        {selectedRowId !== null
          ? `Selected ID: ${selectedRowId}`
          : "Nothing selected"}
      </div>
      <button onClick={() => setSelectedRowId(null)}>Reset Selection</button>
      <button onClick={() => setSelectedRowId(2)}>Select Row 2</button>
    </div>
  );
}

/**
 * Example with sorting and filtering enabled
 */
export function DataGridWithFeatures() {
  const [lastSelectedId, setLastSelectedId] = React.useState<GridRowId | null>(
    null
  );

  const handleSelectionChange = (selectedId: GridRowId) => {
    setLastSelectedId(selectedId);
  };

  return (
    <div style={{ height: 400, width: "100%" }}>
      <DataGridWithRadioSelection
        rows={rows}
        columns={columns}
        onSelectionChange={handleSelectionChange}
        selectRadioAriaLabelFunc={(row) =>
          `Select ${row.firstName} ${row.lastName}`
        }
        initialState={{
          pagination: {
            paginationModel: { pageSize: 3 },
          },
        }}
        pageSizeOptions={[3, 5]}
        data-testid="featured-data-grid"
      />
      <div data-testid="selection-indicator">
        {lastSelectedId !== null
          ? `Selected ID: ${lastSelectedId}`
          : "Nothing selected"}
      </div>
    </div>
  );
}

const CsvExportToolbar = () => {
  const apiRef = useGridApiContext();

  return (
    <GridToolbarContainer sx={{ width: "100%" }}>
      <GridToolbarExportContainer>
        <MenuItem
          onClick={() => {
            apiRef.current?.exportDataAsCsv({
              allColumns: true,
            });
          }}
        >
          Export to CSV
        </MenuItem>
      </GridToolbarExportContainer>
    </GridToolbarContainer>
  );
};

/**
 * For testing how export to CSV export works with the custom selection
 */
export function DataGridWithExportToCsv() {
  return (
    <div style={{ height: 400, width: "100%" }}>
      <DataGridWithRadioSelection
        rows={rows}
        columns={columns}
        onSelectionChange={() => {}}
        selectRadioAriaLabelFunc={(row) =>
          `Select ${row.firstName} ${row.lastName}`
        }
        slots={{
          toolbar: CsvExportToolbar,
        }}
      />
    </div>
  );
}
