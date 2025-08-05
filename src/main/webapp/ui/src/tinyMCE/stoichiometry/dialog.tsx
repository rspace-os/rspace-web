import React from "react";
import { Dialog } from "../../components/DialogBoundary";
import AppBar from "../../components/AppBar";
import DialogTitle from "@mui/material/DialogTitle";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";
import ValidatingSubmitButton, {
  IsValid,
} from "../../components/ValidatingSubmitButton";
import DialogContent from "@mui/material/DialogContent";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import StoichiometryTable, { type StoichiometryTableRef } from "./table";
import useChemicalImport from "../../hooks/api/useChemicalImport";
import { doNotAwait } from "../../util/Util";
import Stack from "@mui/material/Stack";
import { useIntegrationIsAllowedAndEnabled } from "../../hooks/api/integrationHelpers";
import * as FetchingData from "../../util/fetchingData";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";

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
  const { addAlert } = React.useContext(AlertContext);
  const tableRef = React.useRef<StoichiometryTableRef>(null);
  const [showTable, setShowTable] = React.useState(hasStoichiometryTable);
  const [loading, setLoading] = React.useState(false);
  const [saving, setSaving] = React.useState(false);
  const [hasTableChanges, setHasTableChanges] = React.useState(false);
  const [actuallyOpen, setActuallyOpen] = React.useState(false);
  const chemistryStatus = useIntegrationIsAllowedAndEnabled("CHEMISTRY");

  React.useEffect(() => {
    if (open) {
      // Check chemistry integration when dialog is requested to open
      FetchingData.match(chemistryStatus, {
        loading: () => {
          // Don't open dialog while loading
          setActuallyOpen(false);
        },
        error: (error) => {
          setActuallyOpen(false);
          addAlert(
            mkAlert({
              variant: "error",
              title: "Error Checking Chemistry Integration",
              message: `Unable to verify chemistry integration status: ${error}. Please try again later.`,
            }),
          );
        },
        success: (isEnabled) => {
          if (isEnabled) {
            setActuallyOpen(true);
            setShowTable(hasStoichiometryTable);
            setLoading(false);
            setHasTableChanges(false);
          } else {
            setActuallyOpen(false);
            addAlert(
              mkAlert({
                variant: "error",
                title: "Chemistry Integration Disabled",
                message:
                  "The chemistry integration is not enabled. Please contact your administrator to enable it.",
              }),
            );
          }
        },
      });
    } else {
      setActuallyOpen(false);
    }
  }, [open, hasStoichiometryTable, chemistryStatus, addAlert]);

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

  const handleSave = () => {
    setSaving(true);
    doNotAwait(async () => {
      try {
        await tableRef.current?.save();
        console.log("Stoichiometry data saved successfully");
      } catch (e) {
        console.error("Save failed", e);
      } finally {
        setSaving(false);
      }
    })();
  };

  return (
    <Dialog
      open={actuallyOpen}
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
        {actuallyOpen && !showTable && (
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
        {actuallyOpen && showTable && (
          <Stack spacing={2} flexWrap="nowrap">
            <Box>
              <Typography variant="body2">
                Double-click on Mass, Moles, or Notes to edit.
              </Typography>
              <StoichiometryTable
                ref={tableRef}
                chemId={chemId}
                editable
                onChangesUpdate={setHasTableChanges}
              />
            </Box>
          </Stack>
        )}
      </DialogContent>
      <DialogActions>
        {showTable && hasTableChanges && (
          <ValidatingSubmitButton
            onClick={handleSave}
            loading={saving}
            validationResult={IsValid()}
          >
            Save Changes
          </ValidatingSubmitButton>
        )}
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
