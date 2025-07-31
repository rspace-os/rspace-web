import React from "react";
import { DataGrid } from "@mui/x-data-grid";
import CircularProgress from "@mui/material/CircularProgress";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import useChemicalImport, {
  type StoichiometryResponse,
  type StoichiometryMolecule,
} from "../../hooks/api/useChemicalImport";
import { doNotAwait } from "../../util/Util";
import { DataGridColumn } from "../../util/table";

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

  if (
    !data ||
    (!data.reactants?.length &&
      !data.products?.length &&
      !data.moleculeInfo?.length)
  ) {
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

  // Combine all molecules for display
  const allMolecules = [
    ...(data.reactants || []),
    ...(data.products || []),
    ...(data.agents || []),
  ];

  type MoleculeRow = StoichiometryMolecule & { id: number };

  const columns = [
    DataGridColumn.newColumnWithFieldName<"formula", MoleculeRow>("formula", {
      headerName: "Formula",
      flex: 1,
    }),
    DataGridColumn.newColumnWithFieldName<"role", MoleculeRow>("role", {
      headerName: "Role",
      flex: 1,
    }),
    DataGridColumn.newColumnWithFieldName<"mass", MoleculeRow>("mass", {
      headerName: "Mass",
      flex: 1,
    }),
    DataGridColumn.newColumnWithFieldName<"exactMass", MoleculeRow>(
      "exactMass",
      {
        headerName: "Exact Mass",
        flex: 1,
      },
    ),
    DataGridColumn.newColumnWithFieldName<"atomCount", MoleculeRow>(
      "atomCount",
      {
        headerName: "Atom Count",
        flex: 1,
      },
    ),
    DataGridColumn.newColumnWithFieldName<"bondCount", MoleculeRow>(
      "bondCount",
      {
        headerName: "Bond Count",
        flex: 1,
      },
    ),
    DataGridColumn.newColumnWithFieldName<"smiles", MoleculeRow>("smiles", {
      headerName: "SMILES",
      flex: 1,
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
    <Box my={2}>
      <Typography variant="h6" gutterBottom>
        Stoichiometry Table
      </Typography>
      {data.formula && (
        <Typography
          variant="body2"
          gutterBottom
          sx={{ fontFamily: "monospace" }}
        >
          <strong>Formula:</strong> {data.formula}
        </Typography>
      )}
      <DataGrid
        rows={rows}
        columns={columns}
        autoHeight
        disableSelectionOnClick
        hideFooter
      />
    </Box>
  );
}
