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
}: {
  chemId: number | null;
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
  }, [chemId]);

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

  if (!data || !data.molecules || data.molecules.length === 0) {
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

  return (
    <Box my={2}>
      <Typography variant="h6" gutterBottom>
        Stoichiometry Table
      </Typography>
      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Compound</TableCell>
              <TableCell>Role</TableCell>
              <TableCell>Coefficient</TableCell>
              <TableCell>Molecular Mass</TableCell>
              <TableCell>Absolute Mass</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {data.molecules.map((molecule, index) => (
              <TableRow key={index}>
                <TableCell>{molecule.compound || ""}</TableCell>
                <TableCell>{molecule.role || ""}</TableCell>
                <TableCell>{molecule.coefficient || ""}</TableCell>
                <TableCell>{molecule.molecularMass || ""}</TableCell>
                <TableCell>{molecule.absoluteMass || ""}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  );
}
