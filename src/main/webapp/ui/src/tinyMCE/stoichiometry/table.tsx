import React from "react";
import { DataGrid } from "@mui/x-data-grid";
import CircularProgress from "@mui/material/CircularProgress";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import Chip from "@mui/material/Chip";
import useChemicalImport, {
  type StoichiometryResponse,
  type StoichiometryMolecule,
} from "../../hooks/api/useChemicalImport";
import { doNotAwait } from "../../util/Util";
import { DataGridColumn } from "../../util/table";

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

export default function StoichiometryTable({
  chemId,
  useExisting,
}: {
  chemId: number | null;
  useExisting?: boolean;
}): React.ReactNode {
  const { getStoichiometry } = useChemicalImport();
  const [data, setData] = React.useState<StoichiometryResponse | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);

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
  }, [chemId, useExisting]);

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

  // Use the molecules array from the new response format
  const allMolecules = data.molecules;

  type MoleculeRow = StoichiometryMolecule & { id: number };

  const columns = [
    DataGridColumn.newColumnWithFieldName<"name", MoleculeRow>("name", {
      headerName: "Name",
      flex: 1.5,
    }),
    DataGridColumn.newColumnWithFieldName<"formula", MoleculeRow>("formula", {
      headerName: "Formula",
      flex: 1,
    }),
    DataGridColumn.newColumnWithFieldName<"role", MoleculeRow>("role", {
      headerName: "Role",
      flex: 1,
      renderCell: (params) => <RoleChip role={params.value || ""} />,
    }),
    DataGridColumn.newColumnWithFieldName<"coefficient", MoleculeRow>(
      "coefficient",
      {
        headerName: "Coefficient",
        flex: 0.8,
      },
    ),
    DataGridColumn.newColumnWithFieldName<"molecularWeight", MoleculeRow>(
      "molecularWeight",
      {
        headerName: "Molecular Weight",
        flex: 1.2,
      },
    ),
    DataGridColumn.newColumnWithFieldName<"smiles", MoleculeRow>("smiles", {
      headerName: "SMILES",
      flex: 1.5,
      renderCell: (params) => (
        <Box sx={{ fontFamily: "monospace", fontSize: "0.75rem" }}>
          {params.value}
        </Box>
      ),
    }),
  ];

  const rows: MoleculeRow[] = allMolecules.map((molecule, index) => ({
    id: index,
    ...molecule,
  }));

  return (
    <Box sx={{ p: 2 }}>
      <DataGrid
        rows={rows}
        columns={columns}
        autoHeight
        disableSelectionOnClick
        hideFooter
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
        }}
      />
    </Box>
  );
}
