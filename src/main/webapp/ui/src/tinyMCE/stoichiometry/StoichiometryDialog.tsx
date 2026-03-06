import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
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
import CircularProgress from "@mui/material/CircularProgress";
import { useIntegrationIsAllowedAndEnabled } from "../../hooks/api/integrationHelpers";
import * as FetchingData from "../../util/fetchingData";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import { useConfirm } from "../../components/ConfirmProvider";
import ConfirmProvider from "../../components/ConfirmProvider";
import AnalyticsContext from "../../stores/contexts/Analytics";
import useOauthToken from "@/hooks/auth/useOauthToken";
import {
  useCalculateStoichiometryMutation,
  useDeleteStoichiometryMutation,
  useGetMoleculeInfoMutation,
  useUpdateStoichiometryMutation,
} from "@/modules/stoichiometry/mutations";
import StoichiometryTable from "@/tinyMCE/stoichiometry/StoichiometryTable";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false },
    mutations: { retry: false },
  },
});

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
  const { getToken } = useOauthToken();
  const calculateStoichiometryMutation = useCalculateStoichiometryMutation({
    getToken,
  });
  const updateStoichiometryMutation = useUpdateStoichiometryMutation({
    getToken,
  });
  const deleteStoichiometryMutation = useDeleteStoichiometryMutation({
    getToken,
  });
  const getMoleculeInfoMutation = useGetMoleculeInfoMutation({
    getToken,
  });
  const { addAlert } = React.useContext(AlertContext);
  const { trackEvent } = React.useContext(AnalyticsContext);
  const confirm = useConfirm();
  const [showTable, setShowTable] = React.useState(
    stoichiometryId !== undefined,
  );
  const [hasTableChanges, setHasTableChanges] = React.useState(false);
  const [actuallyOpen, setActuallyOpen] = React.useState(false);
  const [tableRequestState, setTableRequestState] = React.useState({
    isSaving: false,
    isDeleting: false,
    isBusy: false,
  });
  const [tableActions, setTableActions] = React.useState<{
    save: () => Promise<void>;
    deleteTable: () => Promise<void>;
  } | null>(null);
  const chemistryStatus = useIntegrationIsAllowedAndEnabled("CHEMISTRY");
  const isRequestInFlight =
    calculateStoichiometryMutation.isPending || tableRequestState.isBusy;

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
    if (isRequestInFlight) {
      return;
    }
    trackEvent("user:create:stoichiometry_table:document_editor");
    void (async () => {
      try {
        if (!chemId) throw new Error("chemId is required");
        const { id, revision } = await calculateStoichiometryMutation.mutateAsync(
          {
            chemId: chemId || undefined,
            recordId,
          },
        );
        setShowTable(true);
        onTableCreated?.(id, revision);
      } catch (e) {
        console.error("Calculation failed", e);
      }
    })();
  };

  const handleSave = () => {
    if (isRequestInFlight) {
      return;
    }
    trackEvent("user:save:stoichiometry_table:document_editor");
    if (!stoichiometryId)
      throw new Error("stoichiometryId is required to save");
    void tableActions?.save();
  };

  const handleDelete = async () => {
    if (!chemId || isRequestInFlight) return;

    const shouldDelete = await confirm(
      "Delete Stoichiometry Table",
      "Are you sure you want to delete this stoichiometry table? This action cannot be undone.",
      "Delete",
      "Cancel",
    );

    if (shouldDelete) {
      trackEvent("user:delete:stoichiometry_table:document_editor");
      void tableActions?.deleteTable();
    }
  };

  async function handleClose() {
    if (isRequestInFlight) {
      return;
    }
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
      disableEscapeKeyDown={isRequestInFlight}
      onClose={(_event, _reason) => {
        if (isRequestInFlight) {
          return;
        }
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
              disabled={isRequestInFlight}
            >
              {calculateStoichiometryMutation.isPending
                ? "Calculating..."
                : "Calculate Stoichiometry"}
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
                <React.Suspense
                  fallback={
                    <Box
                      display="flex"
                      flexDirection="column"
                      justifyContent="center"
                      alignItems="center"
                      minHeight={100}
                      my={2}
                      gap={1}
                    >
                      <CircularProgress
                        size={24}
                        aria-label="Loading stoichiometry table"
                      />
                      <Typography variant="body2" color="textSecondary">
                        Loading stoichiometry table...
                      </Typography>
                    </Box>
                  }
                >
                  <StoichiometryTable
                    editable
                    onChangesUpdate={setHasTableChanges}
                    stoichiometryId={stoichiometryId}
                    stoichiometryRevision={stoichiometryRevision}
                    onRequestStateChange={setTableRequestState}
                    onActionsReady={setTableActions}
                    onUpdateStoichiometry={(params) =>
                      updateStoichiometryMutation.mutateAsync(params)
                    }
                    onDeleteStoichiometry={(params) =>
                      deleteStoichiometryMutation.mutateAsync(params)
                    }
                    onGetMoleculeInfo={(params) =>
                      getMoleculeInfoMutation.mutateAsync(params)
                    }
                    isUpdatingStoichiometry={updateStoichiometryMutation.isPending}
                    isDeletingStoichiometry={deleteStoichiometryMutation.isPending}
                    isGettingMoleculeInfo={getMoleculeInfoMutation.isPending}
                    onSaveSuccess={(revision) => {
                      if (stoichiometryId === undefined) {
                        return;
                      }
                      onSave?.(stoichiometryId, revision);
                      console.log("Stoichiometry data saved successfully");
                    }}
                    onSaveError={(error) => {
                      console.error("Save failed", error);
                    }}
                    onDeleteSuccess={() => {
                      onDelete?.();
                      setShowTable(false);
                      setHasTableChanges(false);
                    }}
                    onDeleteError={(error) => {
                      console.error("Delete failed", error);
                    }}
                  />
                </React.Suspense>
              </Box>
            </Stack>
          )}
      </DialogContent>
      <DialogActions>
        {showTable && hasTableChanges && (
          <ValidatingSubmitButton
            onClick={handleSave}
            loading={tableRequestState.isSaving}
            validationResult={IsValid()}
          >
            Save Changes
          </ValidatingSubmitButton>
        )}
        {showTable && (
          <Button
            onClick={() => {
              void handleDelete();
            }}
            variant="outlined"
            color="error"
            disabled={isRequestInFlight}
          >
            Delete
          </Button>
        )}
        <Button
          disabled={isRequestInFlight}
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
    <QueryClientProvider client={queryClient}>
      <DialogBoundary>
        <ConfirmProvider>
          <StandaloneDialogInner {...props} />
        </ConfirmProvider>
      </DialogBoundary>
    </QueryClientProvider>
  );
}

