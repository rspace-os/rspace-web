import React, { useCallback } from "react";
import {
  DataGrid,
  GridSlotProps,
  GridToolbarColumnsButton,
  GridToolbarContainer,
  GridToolbarExportContainer,
  useGridApiContext,
} from "@mui/x-data-grid";
import CircularProgress from "@mui/material/CircularProgress";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import Chip from "@mui/material/Chip";
import Radio from "@mui/material/Radio";
import MenuItem from "@mui/material/MenuItem";
import { lighten, useTheme } from "@mui/material/styles";
import {
  calculateUpdatedMolecules,
  calculateActualMoles,
} from "./calculations";
import useStoichiometry, {
  type StoichiometryResponse,
  type StoichiometryMolecule,
} from "../../hooks/api/useStoichiometry";
import { doNotAwait } from "../../util/Util";
import { DataGridColumn } from "../../util/table";

declare module "@mui/x-data-grid" {
  interface ToolbarPropsOverrides {
    setColumnsMenuAnchorEl: (anchorEl: HTMLElement | null) => void;
  }
}

export interface StoichiometryTableRef {
  save: () => Promise<void>;
  delete: () => Promise<void>;
}

const RoleChip = ({ role }: { role: string }) => {
  const getRoleColor = (role: string) => {
    switch (role.toLowerCase()) {
      case "reactant":
        return { color: "#1566b7", backgroundColor: "#f5fbfe" }; // Blue
      case "product":
        return { color: "#2e7d32", backgroundColor: "#e8f5e9" }; // Green
      case "catalyst":
        return { color: "#7b1fa2", backgroundColor: "#f3e5f5" }; // Purple
      case "agent":
        return { color: "#ed6c02", backgroundColor: "#fff3e0" }; // Orange
      default:
        return { color: "#616161", backgroundColor: "#f5f5f5" }; // Grey
    }
  };

  const { color, backgroundColor } = getRoleColor(role);

  return (
    <Chip
      label={role.toLowerCase()}
      size="small"
      sx={{
        color: `${color} !important`,
        backgroundColor: `${backgroundColor} !important`,
        border: `1px solid ${color}`,
        fontWeight: 500,
        textTransform: "lowercase",
        "&.MuiChip-filled": {
          backgroundColor: `${backgroundColor} !important`,
          border: `1px solid ${color}`,
        },
      }}
    />
  );
};

function Toolbar({
  setColumnsMenuAnchorEl,
}: GridSlotProps["toolbar"]): React.ReactNode {
  const apiRef = useGridApiContext();
  const theme = useTheme();

  /**
   * The columns menu can be opened by either tapping the "Columns" toolbar
   * button or by tapping the "Manage columns" menu item in each column's menu,
   * logic that is handled my MUI. We provide a custom `anchorEl` so that the
   * menu is positioned beneath the "Columns" toolbar button to be consistent
   * with the other toolbar menus, otherwise is appears far to the left. Rather
   * than having to hook into the logic that triggers the opening of the
   * columns menu in both places, we just set the `anchorEl` pre-emptively.
   */
  const columnMenuRef = React.useRef<HTMLButtonElement>();
  React.useEffect(() => {
    if (columnMenuRef.current) setColumnsMenuAnchorEl(columnMenuRef.current);
  }, [setColumnsMenuAnchorEl]);

  return (
    <GridToolbarContainer sx={{ mr: `${theme.spacing(0.5)} !important` }}>
      <Box flexGrow={1}></Box>
      <GridToolbarColumnsButton
        ref={(node) => {
          if (node) columnMenuRef.current = node;
        }}
      />
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
}

const StoichiometryTable = React.forwardRef<
  StoichiometryTableRef,
  {
    chemId: number | null;
    editable?: boolean;
    onChangesUpdate?: (hasChanges: boolean) => void;
  }
>(function StoichiometryTable(
  { chemId, editable = false, onChangesUpdate },
  ref,
) {
  const { getStoichiometry, updateStoichiometry, deleteStoichiometry } =
    useStoichiometry();
  const theme = useTheme();
  const [data, setData] = React.useState<StoichiometryResponse | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [allMolecules, setAllMolecules] = React.useState<
    ReadonlyArray<StoichiometryMolecule>
  >([]);
  const [columnsMenuAnchorEl, setColumnsMenuAnchorEl] =
    React.useState<HTMLElement | null>(null);

  React.useEffect(() => {
    setLoading(true);
    setError(null);
    doNotAwait(async () => {
      try {
        if (!chemId) throw new Error("chemId is required");
        const result = await getStoichiometry({ chemId });
        setData(result);
      } catch (e) {
        console.error(e);
        setError("Failed to load stoichiometry data");
      } finally {
        setLoading(false);
      }
    })();
  }, [chemId]);

  React.useEffect(() => {
    if (data?.molecules) {
      const molecules = data.molecules;
      const hasLimitingReagent = molecules.some(
        (m) => m.limitingReagent && m.role.toLowerCase() === "reactant",
      );

      // Usability enhancement: default first reactant as limiting reagent since it's usually the limiting one
      if (!hasLimitingReagent) {
        const firstReactant = molecules.find(
          (m) => m.role.toLowerCase() === "reactant",
        );

        if (firstReactant) {
          const updatedMolecules = molecules.map((m) =>
            m.id === firstReactant.id ? { ...m, limitingReagent: true } : m,
          );
          setAllMolecules(updatedMolecules);
        } else {
          setAllMolecules(molecules);
        }
      } else {
        setAllMolecules(molecules);
      }
    }
  }, [data]);

  React.useImperativeHandle(
    ref,
    () => ({
      save: async () => {
        if (!data || !data.id) {
          throw new Error("No stoichiometry data to save");
        }

        const updatedData: StoichiometryResponse = {
          ...data,
          molecules: allMolecules,
        };

        await updateStoichiometry({
          stoichiometryId: data.id,
          stoichiometryData: updatedData,
        });
        onChangesUpdate?.(false);
      },
      delete: async () => {
        if (!data || !data.id) {
          throw new Error("No stoichiometry data to delete");
        }
        await deleteStoichiometry({ stoichiometryId: data.id });
        setData(null);
        setAllMolecules([]);
        onChangesUpdate?.(false);
      },
    }),
    [data, allMolecules, updateStoichiometry],
  );

  if (loading) {
    return (
      <Box
        display="flex"
        flexDirection="column"
        justifyContent="center"
        alignItems="center"
        minHeight={100}
        my={2}
        gap={1}
      >
        <CircularProgress size={24} />
        <Typography variant="body2" color="textSecondary">
          Loading stoichiometry table...
        </Typography>
      </Box>
    );
  }

  if (error) {
    return (
      <Box
        display="flex"
        justifyContent="center"
        alignItems="center"
        minHeight={100}
        my={2}
      >
        <Typography color="error" variant="body2">
          {error}
        </Typography>
      </Box>
    );
  }

  if (!data || !data.molecules?.length) {
    return (
      <Box
        display="flex"
        justifyContent="center"
        alignItems="center"
        minHeight={100}
        my={2}
      >
        <Typography variant="body2">No stoichiometry data available</Typography>
      </Box>
    );
  }

  const limitingReagent = allMolecules.find(
    (m) => m.limitingReagent && m.role.toLowerCase() === "reactant",
  );

  const columns = [
    DataGridColumn.newColumnWithFieldName<"name", StoichiometryMolecule>(
      "name",
      {
        headerName: "Name",
        flex: 1.5,
      },
    ),
    DataGridColumn.newColumnWithFieldName<"role", StoichiometryMolecule>(
      "role",
      {
        headerName: "Role",
        flex: 1,
        renderCell: (params) => <RoleChip role={params.value || ""} />,
      },
    ),
    DataGridColumn.newColumnWithFieldName<
      "limitingReagent",
      StoichiometryMolecule
    >("limitingReagent", {
      headerName: "Limiting Reagent",
      flex: 1,
      renderCell: (params) =>
        params.row.role.toLowerCase() === "reactant" ? (
          <Radio
            checked={params.row.limitingReagent || false}
            disabled={!editable}
            inputProps={{
              "aria-label": `Select ${params.row.name} as limiting reagent`,
            }}
            onChange={(e) => {
              if (e.target.checked && editable) {
                const updatedRow = { ...params.row, limitingReagent: true };
                const newMolecules = calculateUpdatedMolecules(
                  allMolecules,
                  updatedRow,
                );
                setAllMolecules(newMolecules);
                onChangesUpdate?.(true);
              }
            }}
          />
        ) : (
          <>&mdash;</>
        ),
    }),
    DataGridColumn.newColumnWithFieldName<"coefficient", StoichiometryMolecule>(
      "coefficient",
      {
        headerName: "Equivalent",
        flex: 1,
        // @ts-expect-error It's not documented or typed, but editable can be a function
        editable: (params) => {
          if (limitingReagent && params.id === limitingReagent.id) {
            return false;
          }
          return editable;
        },
        type: "number",
        cellClassName: (params) => {
          if (limitingReagent && params.id === limitingReagent.id) {
            return "stoichiometry-disabled-cell";
          }
          return "";
        },
      },
    ),
    DataGridColumn.newColumnWithFieldName<
      "molecularWeight",
      StoichiometryMolecule
    >("molecularWeight", {
      headerName: "Molecular Weight (g/mol)",
      flex: 1.2,
    }),
    DataGridColumn.newColumnWithFieldName<"mass", StoichiometryMolecule>(
      "mass",
      {
        headerName: "Mass (g)",
        flex: 1,
        // @ts-expect-error It's not documented or typed, but editable can be a function
        editable: (params) => {
          if (limitingReagent) {
            return editable && params.id === limitingReagent.id;
          }
          return editable;
        },
        type: "number",
        renderCell: (params) => params.value ?? <>&#8212;</>,
        cellClassName: (params) => {
          if (limitingReagent && params.id !== limitingReagent.id) {
            return "stoichiometry-disabled-cell";
          }
          return "";
        },
      },
    ),
    DataGridColumn.newColumnWithFieldName<"moles", StoichiometryMolecule>(
      "moles",
      {
        headerName: "Moles (mol)",
        flex: 1,
        // @ts-expect-error It's not documented or typed, but editable can be a function
        editable: (params) => {
          if (limitingReagent) {
            return editable && params.id === limitingReagent.id;
          }
          return editable;
        },
        type: "number",
        renderCell: (params) => params.value ?? <>&#8212;</>,
        cellClassName: (params) => {
          if (limitingReagent && params.id !== limitingReagent.id) {
            return "stoichiometry-disabled-cell";
          }
          return "";
        },
      },
    ),
    DataGridColumn.newColumnWithFieldName<
      "actualAmount",
      StoichiometryMolecule
    >("actualAmount", {
      headerName: "Actual Mass (g)",
      flex: 1,
      editable: editable,
      type: "number",
      renderCell: (params) => params.value ?? <>&mdash;</>,
    }),
    DataGridColumn.newColumnWithValueGetter<
      "actualMoles",
      StoichiometryMolecule,
      number | null
    >(
      "actualMoles",
      (row: StoichiometryMolecule) =>
        calculateActualMoles({
          actualAmount: row.actualAmount,
          molecularWeight: row.molecularWeight,
        }),
      {
        headerName: "Actual Moles (mol)",
        flex: 1,
        type: "number",
        editable: false,
        renderCell: (params) => {
          return params.value !== null ? params.value : <>&mdash;</>;
        },
      },
    ),
    DataGridColumn.newColumnWithFieldName<"actualYield", StoichiometryMolecule>(
      "actualYield",
      {
        headerName: "Yield (%)",
        flex: 1,
        type: "number",
        editable: false,
        renderCell: (params) => {
          const value = params.value;
          return value !== null && value !== undefined ? (
            `${value}%`
          ) : (
            <>&mdash;</>
          );
        },
      },
    ),
    DataGridColumn.newColumnWithFieldName<"notes", StoichiometryMolecule>(
      "notes",
      {
        headerName: "Notes",
        flex: 1.5,
        editable: editable,
        type: "string",
        renderCell: (params) => params.value ?? <>&mdash;</>,
      },
    ),
  ];

  return (
    <DataGrid
      rows={allMolecules ?? []}
      columns={columns}
      autoHeight
      hideFooter
      getRowId={(row) => row.id}
      processRowUpdate={(newRow) => {
        const newMolecules = calculateUpdatedMolecules(allMolecules, newRow);
        setAllMolecules(newMolecules);
        onChangesUpdate?.(true);
        return newMolecules.find((m) => m.id === newRow.id) || newRow;
      }}
      slots={{
        toolbar: Toolbar,
      }}
      slotProps={{
        toolbar: {
          setColumnsMenuAnchorEl,
        },
        panel: {
          anchorEl: columnsMenuAnchorEl,
        },
      }}
      sx={{
        border: "none",
        "& .MuiDataGrid-columnHeaders": {
          backgroundColor: "#f8f9fa",
          borderBottom: "2px solid #e0e0e0",
        },
        "& .MuiDataGrid-cell": {
          borderBottom: "1px solid #f0f0f0",
        },
        "& .MuiDataGrid-row:hover": {
          backgroundColor: "#f8f9fa",
        },
        "& .stoichiometry-disabled-cell": {
          backgroundColor: `${lighten(theme.palette.primary.background, 0.3)} !important`,
          color: `${theme.palette.primary.contrastText} !important`,
          fontStyle: "italic",
          "&:hover": {
            backgroundColor: `${theme.palette.action.hover} !important`,
          },
        },
      }}
    />
  );
});

export default StoichiometryTable;
