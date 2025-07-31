import React from "react";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Paper from "@mui/material/Paper";
import CircularProgress from "@mui/material/CircularProgress";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import useChemicalImport, {
  type StoichiometryResponse,
} from "../../hooks/api/useChemicalImport";
import { doNotAwait } from "../../util/Util";

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
      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Formula</TableCell>
              <TableCell>Role</TableCell>
              <TableCell>Mass</TableCell>
              <TableCell>Exact Mass</TableCell>
              <TableCell>Atom Count</TableCell>
              <TableCell>Bond Count</TableCell>
              <TableCell>SMILES</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {allMolecules.map((molecule, index) => (
              <TableRow key={index}>
                <TableCell>{molecule.formula || ""}</TableCell>
                <TableCell>{molecule.role || ""}</TableCell>
                <TableCell>{molecule.mass || ""}</TableCell>
                <TableCell>{molecule.exactMass || ""}</TableCell>
                <TableCell>{molecule.atomCount || ""}</TableCell>
                <TableCell>{molecule.bondCount || ""}</TableCell>
                <TableCell
                  sx={{ fontFamily: "monospace", fontSize: "0.75rem" }}
                >
                  {molecule.smiles || ""}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  );
}
