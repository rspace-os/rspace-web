import React from "react";
import { DataGrid } from "@mui/x-data-grid";
import CircularProgress from "@mui/material/CircularProgress";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import Chip from "@mui/material/Chip";
import Radio from "@mui/material/Radio";
import { useTheme } from "@mui/material/styles";
import useStoichiometry, {
  type StoichiometryResponse,
  type StoichiometryMolecule,
} from "../../hooks/api/useStoichiometry";
import { doNotAwait } from "../../util/Util";
import { DataGridColumn } from "../../util/table";

// Pure function to calculate updated molecules based on edits
function calculateUpdatedMolecules(
  allMolecules: StoichiometryMolecule[],
  editedRow: StoichiometryMolecule,
): StoichiometryMolecule[] {
  const updatedMolecules = allMolecules.map((molecule) => ({ ...molecule }));
  const editedMolecule = updatedMolecules.find((m) => m.id === editedRow.id);

  if (!editedMolecule) return updatedMolecules;

  // Update the edited molecule with new state
  editedMolecule.mass = editedRow.mass;
  editedMolecule.moles = editedRow.moles;
  editedMolecule.notes = editedRow.notes;
  editedMolecule.coefficient = editedRow.coefficient;
  editedMolecule.limitingReagent = editedRow.limitingReagent;

  // Handle mass <-> moles conversion for the edited molecule
  // We need to determine which field was actually changed to avoid circular updates
  if (editedMolecule.molecularWeight && editedMolecule.molecularWeight > 0) {
    const originalMolecule = allMolecules.find((m) => m.id === editedRow.id);

    if (originalMolecule) {
      // If mass changed, calculate moles
      if (
        editedMolecule.mass !== originalMolecule.mass &&
        editedMolecule.mass &&
        editedMolecule.mass > 0
      ) {
        editedMolecule.moles = Number(
          (editedMolecule.mass / editedMolecule.molecularWeight).toFixed(6),
        );
      }
      // If moles changed, calculate mass
      else if (
        editedMolecule.moles !== originalMolecule.moles &&
        editedMolecule.moles &&
        editedMolecule.moles > 0
      ) {
        editedMolecule.mass = Number(
          (editedMolecule.moles * editedMolecule.molecularWeight).toFixed(4),
        );
      }
    }
  }

  // Handle limiting reagent selection
  if (editedMolecule.limitingReagent) {
    // Clear all other limiting reagents
    updatedMolecules.forEach((molecule) => {
      if (
        molecule.id !== editedMolecule.id &&
        molecule.role.toLowerCase() === "reactant"
      ) {
        molecule.limitingReagent = false;
      }
    });
  }

  // Perform stoichiometric calculations if there's a limiting reagent
  const limitingReagent = updatedMolecules.find(
    (m) =>
      m.limitingReagent &&
      m.role.toLowerCase() === "reactant" &&
      m.moles &&
      m.moles > 0,
  );

  if (limitingReagent) {
    const limitingMoles = limitingReagent.moles || 0;
    const limitingCoeff = limitingReagent.coefficient || 1;

    updatedMolecules.forEach((molecule) => {
      if (molecule.id === limitingReagent.id) return; // Skip the limiting reagent itself

      const coeff = molecule.coefficient || 1;
      // Calculate theoretical moles based on stoichiometry
      const theoreticalMoles = (limitingMoles / limitingCoeff) * coeff;

      molecule.moles = Number(theoreticalMoles.toFixed(6));
      if (molecule.molecularWeight) {
        molecule.mass = Number(
          (theoreticalMoles * molecule.molecularWeight).toFixed(4),
        );
      }
    });
  }

  return updatedMolecules;
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

function StoichiometryTable({
  chemId,
  editable = false,
}: {
  chemId: number | null;
  editable?: boolean;
}): React.ReactNode {
  const { getStoichiometry } = useStoichiometry();
  const theme = useTheme();
  const [data, setData] = React.useState<StoichiometryResponse | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [allMolecules, setAllMolecules] = React.useState<
    StoichiometryMolecule[]
  >([]);

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
      setAllMolecules(data.molecules);
    }
  }, [data]);

  if (loading) {
    return (
      <Box
        display="flex"
        justifyContent="center"
        alignItems="center"
        minHeight={100}
        my={2}
      >
        <CircularProgress size={24} />
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

  // allMolecules state is now defined at the top of the component
  
  // Find the current limiting reagent
  const limitingReagent = allMolecules.find(
    (m) => m.limitingReagent && m.role.toLowerCase() === "reactant"
  );

  type MoleculeRow = StoichiometryMolecule;

  const columns = [
    DataGridColumn.newColumnWithFieldName<"name", MoleculeRow>("name", {
      headerName: "Name",
      flex: 1.5,
    }),
    DataGridColumn.newColumnWithFieldName<"role", MoleculeRow>("role", {
      headerName: "Role",
      flex: 1,
      renderCell: (params) => <RoleChip role={params.value || ""} />,
    }),
    DataGridColumn.newColumnWithFieldName<"limitingReagent", MoleculeRow>(
      "limitingReagent",
      {
        headerName: "Limiting Reagent",
        flex: 1,
        renderCell: (params) =>
          params.row.role.toLowerCase() === "reactant" ? (
            <Radio
              checked={params.row.limitingReagent || false}
              disabled={!editable}
              onChange={(e) => {
                if (e.target.checked && editable) {
                  const updatedRow = { ...params.row, limitingReagent: true };
                  const newMolecules = calculateUpdatedMolecules(
                    allMolecules,
                    updatedRow,
                  );
                  setAllMolecules(newMolecules);
                }
              }}
            />
          ) : (
            <>&mdash;</>
          ),
      },
    ),
    DataGridColumn.newColumnWithFieldName<"coefficient", MoleculeRow>(
      "coefficient",
      {
        headerName: "Equivalent",
        flex: 1,
        editable: (params) => {
          // Limiting reagent's coefficient should not be editable
          if (limitingReagent && params.id === limitingReagent.id) {
            return false;
          }
          return editable;
        },
        type: "number",
        cellClassName: (params) => {
          // Add visual styling for disabled cells
          if (limitingReagent && params.id === limitingReagent.id) {
            return "stoichiometry-disabled-cell";
          }
          return "";
        },
      },
    ),
    DataGridColumn.newColumnWithFieldName<"molecularWeight", MoleculeRow>(
      "molecularWeight",
      {
        headerName: "Molecular Weight (g/mol)",
        flex: 1.2,
      },
    ),
    DataGridColumn.newColumnWithFieldName<"mass", MoleculeRow>("mass", {
      headerName: "Mass (g)",
      flex: 1,
      editable: (params) => {
        // When there's a limiting reagent, only the limiting reagent's mass is editable
        if (limitingReagent) {
          return editable && params.id === limitingReagent.id;
        }
        return editable;
      },
      type: "number",
      renderCell: (params) => params.value ?? <>&#8212;</>,
      cellClassName: (params) => {
        // Add visual styling for disabled cells
        if (limitingReagent && params.id !== limitingReagent.id) {
          return "stoichiometry-disabled-cell";
        }
        return "";
      },
    }),
    DataGridColumn.newColumnWithFieldName<"moles", MoleculeRow>("moles", {
      headerName: "Moles (mol)",
      flex: 1,
      editable: (params) => {
        // When there's a limiting reagent, only the limiting reagent's moles is editable
        if (limitingReagent) {
          return editable && params.id === limitingReagent.id;
        }
        return editable;
      },
      type: "number",
      renderCell: (params) => params.value ?? <>&#8212;</>,
      cellClassName: (params) => {
        // Add visual styling for disabled cells
        if (limitingReagent && params.id !== limitingReagent.id) {
          return "stoichiometry-disabled-cell";
        }
        return "";
      },
    }),
    DataGridColumn.newColumnWithFieldName<"notes", MoleculeRow>("notes", {
      headerName: "Notes",
      flex: 1.5,
      editable: editable,
      type: "string",
      renderCell: (params) => params.value ?? <>&mdash;</>,
    }),
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
        return newMolecules.find((m) => m.id === newRow.id) || newRow;
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
          backgroundColor: `${theme.palette.action.disabled} !important`,
          color: `${theme.palette.text.disabled} !important`,
          fontStyle: "italic",
          "&:hover": {
            backgroundColor: `${theme.palette.action.hover} !important`,
          },
        },
      }}
    />
  );
}

export default StoichiometryTable;
