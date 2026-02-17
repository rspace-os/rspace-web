import React from "react";
import { Dialog, DialogBoundary } from "../../components/DialogBoundary";
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
import Stack from "@mui/material/Stack";
import { useIntegrationIsAllowedAndEnabled } from "../../hooks/api/integrationHelpers";
import * as FetchingData from "../../util/fetchingData";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import { useConfirm } from "../../components/ConfirmProvider";
import ConfirmProvider from "../../components/ConfirmProvider";
import useStoichiometry from "@/hooks/api/useStoichiometry";
import AnalyticsContext from "../../stores/contexts/Analytics";
import StoichiometryTable from "@/tinyMCE/stoichiometry/StoichiometryTable";
import { StoichiometryTableRef } from "@/tinyMCE/stoichiometry/types";

function StandaloneDialogInner({
  open,
  onClose,
  chemId,
  recordId,
  stoichiometryId,
  stoichiometryRevision,
  onTableCreated,
  onSave,
  onDelete,
}: {
  open: boolean;
  onClose: () => void;
  chemId: number | null;
  recordId: number;
  stoichiometryId: number | undefined;
  stoichiometryRevision: number | undefined;
  onTableCreated?: (id: number, version: number) => void;
  onSave?: (id: number, version: number) => void;
  onDelete?: () => void;
}): React.ReactNode {
  const titleId = React.useId();
  const { calculateStoichiometry } = useStoichiometry();
  const { addAlert } = React.useContext(AlertContext);
  const { trackEvent } = React.useContext(AnalyticsContext);
  const confirm = useConfirm();
  const tableRef = React.useRef<StoichiometryTableRef>(null);
  const [showTable, setShowTable] = React.useState(
    stoichiometryId !== undefined,
  );
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
            setShowTable(stoichiometryId !== undefined);
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
  }, [open, stoichiometryId, chemistryStatus]);

  const handleCalculate = () => {
    trackEvent("user:create:stoichiometry_table:document_editor");
    setLoading(true);
    void (async () => {
      try {
        if (!chemId) throw new Error("chemId is required");
        const { id, revision } = await calculateStoichiometry({
          chemId: chemId || undefined,
          recordId: recordId,
        });
        setShowTable(true);
        onTableCreated?.(id, revision);
      } catch (e) {
        console.error("Calculation failed", e);
      } finally {
        setLoading(false);
      }
    })();
  };

  const handleSave = () => {
    trackEvent("user:save:stoichiometry_table:document_editor");
    setSaving(true);
    if (!stoichiometryId)
      throw new Error("stoichiometryId is required to save");
    void (async () => {
      try {
        const newRevision = await tableRef.current?.save();
        // @ts-expect-error To fix
        onSave?.(stoichiometryId, newRevision);
        console.log("Stoichiometry data saved successfully");
      } catch (e) {
        console.error("Save failed", e);
      } finally {
        setSaving(false);
      }
    })();
  };

  const handleDelete = async () => {
    if (!chemId) return;

    const shouldDelete = await confirm(
      "Delete Stoichiometry Table",
      "Are you sure you want to delete this stoichiometry table? This action cannot be undone.",
      "Delete",
      "Cancel",
    );

    if (shouldDelete) {
      trackEvent("user:delete:stoichiometry_table:document_editor");
      try {
        await tableRef.current?.delete();
        onDelete?.();
        setShowTable(false);
        setHasTableChanges(false);
      } catch (e) {
        console.error("Delete failed", e);
      }
    }
  };

  async function handleClose() {
    if (
      hasTableChanges &&
      !(await confirm(
        "Discard changes?",
        "Closing the dialog will discard the unsaved changes.",
        "Discard",
        "Cancel",
      ))
    )
      return;
    onClose();
  }

  return (
    <Dialog
      open={actuallyOpen}
      onClose={() => {
        void handleClose();
      }}
      aria-labelledby={titleId}
      maxWidth="xl"
      fullWidth
    >
      <AppBar
        variant="dialog"
        currentPage="Chemistry"
        accessibilityTips={{
          supportsHighContrastMode: true,
        }}
      />
      <DialogTitle id={titleId} component="h3">
        Reaction Table
      </DialogTitle>
      <DialogContent>
        {actuallyOpen && stoichiometryId === undefined && (
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
        {actuallyOpen &&
          stoichiometryId !== undefined &&
          stoichiometryRevision !== undefined && (
            <Stack spacing={2} flexWrap="nowrap">
              <Box>
                <Typography variant="body2">
                  Double-click to edit Equivalent, Mass, Moles, Actual Mass,
                  Actual Moles, or Notes. Yield/Excess values are calculated
                  automatically, as are each pairing of moles and mass.
                </Typography>
                <StoichiometryTable
                  ref={tableRef}
                  editable
                  onChangesUpdate={setHasTableChanges}
                  stoichiometryId={stoichiometryId}
                  stoichiometryRevision={stoichiometryRevision}
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
        {showTable && (
          // eslint-disable-next-line @typescript-eslint/no-misused-promises
          <Button onClick={handleDelete} variant="outlined" color="error">
            Delete
          </Button>
        )}
        <Button
          onClick={() => {
            void handleClose();
          }}
        >
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default function StoichiometryDialog(
  props: React.ComponentProps<typeof StandaloneDialogInner>,
): React.ReactNode {
  return (
    <DialogBoundary>
      <ConfirmProvider>
        <StandaloneDialogInner {...props} />
      </ConfirmProvider>
    </DialogBoundary>
  );
}
