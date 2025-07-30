import React from "react";
import { Dialog } from "../../components/DialogBoundary";
import AppBar from "../../components/AppBar";
import DialogTitle from "@mui/material/DialogTitle";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";
import DialogContent from "@mui/material/DialogContent";
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

export default function StandaloneDialog({
  open,
  onClose,
  chemId,
}: {
  open: boolean;
  onClose: () => void;
  chemId: number | null;
}): React.ReactNode {
  const titleId = React.useId();
  const { getStoichiometry } = useChemicalImport();
  const [data, setData] = React.useState<StoichiometryResponse | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (!open) {
      setData(null);
      setError(null);
    } else {
      setLoading(true);
      setError(null);
      if (!chemId) throw new Error("chemId is required");
      doNotAwait(async () => {
        try {
          console.debug({ chemId });
          const result = await getStoichiometry({ chemId });
          setData(result);
        } catch (e) {
          console.error(e);
          setError("Failed to load stoichiometry data");
        } finally {
          setLoading(false);
        }
      })();
    }
  }, [open, chemId]);

  const renderContent = () => {
    if (loading) {
      return (
        <Box
          display="flex"
          justifyContent="center"
          alignItems="center"
          minHeight={200}
        >
          <CircularProgress />
        </Box>
      );
    }

    if (error) {
      return (
        <Box
          display="flex"
          justifyContent="center"
          alignItems="center"
          minHeight={200}
        >
          <Typography color="error">{error}</Typography>
        </Box>
      );
    }

    console.debug(data);

    if (!data || !data.molecules || data.molecules.length === 0) {
      return (
        <Box
          display="flex"
          justifyContent="center"
          alignItems="center"
          minHeight={200}
        >
          <Typography>No stoichiometry data available</Typography>
        </Box>
      );
    }

    return (
      <TableContainer component={Paper}>
        <Table>
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
    );
  };

  return (
    <Dialog
      open={open}
      onClose={onClose}
      aria-labelledby={titleId}
      maxWidth="md"
      fullWidth
    >
      <AppBar
        variant="dialog"
        currentPage="Stoichiometry"
        accessibilityTips={{}}
      />
      <DialogTitle id={titleId} component="h3">
        Stoichiometry Calculator
      </DialogTitle>
      <DialogContent>{renderContent()}</DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
