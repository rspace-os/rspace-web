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
import Stack from "@mui/material/Stack";

export default function StandaloneDialog({
  open,
  onClose,
  chemId,
  hasStoichiometryTable,
  onTableCreated,
}: {
  open: boolean;
  onClose: () => void;
  chemId: number | null;
  hasStoichiometryTable: boolean;
  onTableCreated?: () => void;
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
        onTableCreated?.();
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
      maxWidth="xl"
      fullWidth
    >
      <AppBar variant="dialog" currentPage="Chemistry" accessibilityTips={{}} />
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
          <Stack spacing={2} flexWrap="nowrap">
            <Box>
              <Typography variant="body2">
                Double-click on Mass, Moles, or Notes to edit.
              </Typography>
              <StoichiometryTable chemId={chemId} editable />
            </Box>
          </Stack>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
