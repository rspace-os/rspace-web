import React from "react";
import { Dialog } from "../../components/DialogBoundary";
import AppBar from "../../components/AppBar";
import DialogTitle from "@mui/material/DialogTitle";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";
import DialogContent from "@mui/material/DialogContent";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import StoichiometryTable from "./table";
import useChemicalImport from "../../hooks/api/useChemicalImport";
import { doNotAwait } from "../../util/Util";

export default function StandaloneDialog({
  open,
  onClose,
  chemId,
  hasStoichiometryTable,
}: {
  open: boolean;
  onClose: () => void;
  chemId: number | null;
  hasStoichiometryTable: boolean;
}): React.ReactNode {
  const titleId = React.useId();
  const { calculateStoichiometry } = useChemicalImport();
  const [showTable, setShowTable] = React.useState(hasStoichiometryTable);
  const [loading, setLoading] = React.useState(false);

  React.useEffect(() => {
    if (open) {
      setShowTable(hasStoichiometryTable);
      setLoading(false);
    }
  }, [open]);

  const handleCalculate = () => {
    setLoading(true);
    doNotAwait(async () => {
      try {
        if (!chemId) throw new Error("chemId is required");
        await calculateStoichiometry({ chemId });
        setShowTable(true);
      } catch (e) {
        console.error("Calculation failed", e);
      } finally {
        setLoading(false);
      }
    })();
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
      <DialogContent>
        {open && !showTable && (
          <Box
            display="flex"
            flexDirection="column"
            alignItems="center"
            gap={2}
            py={4}
          >
            <Typography variant="body1" align="center">
              Click the button below to calculate the stoichiometry data for
              this chemical compound.
            </Typography>
            <Button
              variant="contained"
              color="primary"
              onClick={handleCalculate}
              disabled={loading}
            >
              {loading ? "Calculating..." : "Calculate Stoichiometry"}
            </Button>
          </Box>
        )}
        {open && showTable && (
          <StoichiometryTable chemId={chemId} />
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
