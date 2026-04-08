import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Dialog, DialogBoundary } from "@/components/DialogBoundary";
import AppBar from "../../components/AppBar";
import DialogTitle from "@mui/material/DialogTitle";
import DialogActions from "@mui/material/DialogActions";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import ValidatingSubmitButton, {
  IsValid,
} from "../../components/ValidatingSubmitButton";
import DialogContent from "@mui/material/DialogContent";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import Stack from "@mui/material/Stack";
import CircularProgress from "@mui/material/CircularProgress";
import { useIntegrationIsAllowedAndEnabled } from "@/hooks/api/integrationHelpers";
import * as FetchingData from "../../util/fetchingData";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import { useConfirm } from "@/components/ConfirmProvider";
import ConfirmProvider from "../../components/ConfirmProvider";
import AnalyticsContext from "../../stores/contexts/Analytics";
import useOauthToken from "@/hooks/auth/useOauthToken";
import { useCalculateStoichiometryMutation } from "@/modules/stoichiometry/mutations";
import StoichiometryTable from "@/tinyMCE/stoichiometry/StoichiometryTable";
import { StoichiometryTableControllerProvider } from "@/tinyMCE/stoichiometry/StoichiometryTableControllerContext";
import { useEditableStoichiometryTable } from "@/tinyMCE/stoichiometry/useEditableStoichiometryTable";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false },
    mutations: { retry: false },
  },
});

function areSameStoichiometry(
  left: { id: number; revision: number } | null,
  right: { id: number; revision: number } | null,
) {
  return left?.id === right?.id && left?.revision === right?.revision;
}

const StoichiometryTableLoadingFallback = ({
  disableClose,
}: {
  disableClose: boolean;
}) => {
  return (
    <>
      <DialogContent>
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
      </DialogContent>
      <DialogActions>
        <Button disabled={disableClose}>Delete</Button>
        <Button disabled={disableClose}>Close</Button>
      </DialogActions>
    </>
  );
};

const EditableStoichiometryDialogSection = ({
  currentStoichiometry,
  onClose,
  onSave,
  onDelete,
  setCurrentStoichiometry,
  registerCloseHandler,
}: {
  currentStoichiometry: {
    id: number;
    revision: number;
  };
  onClose: () => void;
  onSave?: (id: number, version: number) => void;
  onDelete?: () => void;
  setCurrentStoichiometry: React.Dispatch<
    React.SetStateAction<{
      id: number;
      revision: number;
    } | null>
  >;
  registerCloseHandler?: (handler: (() => Promise<void>) | null) => void;
}) => {
  const { trackEvent } = React.useContext(AnalyticsContext);
  const confirm = useConfirm();
  const {
    hasChanges,
    isBusy,
    isSaving,
    save,
    deleteTable,
    tableController,
  } = useEditableStoichiometryTable({
    stoichiometryId: currentStoichiometry.id,
    stoichiometryRevision: currentStoichiometry.revision,
  });

  const tableControllerWithRefresh = React.useMemo(
    () => ({
      ...tableController,
      updateInventoryStock: async (selectedMoleculeIds: number[]) => {
        const result = await tableController.updateInventoryStock(selectedMoleculeIds);

        if (result.refreshedStoichiometry) {
          setCurrentStoichiometry(result.refreshedStoichiometry);
        }

        return result;
      },
    }),
    [setCurrentStoichiometry, tableController],
  );

  const handleClose = React.useCallback(async () => {
    if (isBusy) {
      return;
    }
    if (
      hasChanges &&
      !(await confirm(
        "Discard changes?",
        "Closing the dialog will discard the unsaved changes.",
        "Discard",
        "Cancel",
      ))
    ) {
      return;
    }
    onClose();
  }, [confirm, hasChanges, isBusy, onClose]);

  React.useEffect(() => {
    registerCloseHandler?.(handleClose);
    return () => {
      registerCloseHandler?.(null);
    };
  }, [handleClose, registerCloseHandler]);

  const handleSave = React.useCallback(() => {
    if (isBusy) {
      return;
    }
    trackEvent("user:save:stoichiometry_table:document_editor");
    void (async () => {
      try {
        const revision = await save();
        const updatedStoichiometry = {
          id: currentStoichiometry.id,
          revision,
        };
        setCurrentStoichiometry(updatedStoichiometry);
        onSave?.(updatedStoichiometry.id, updatedStoichiometry.revision);
        console.log("Stoichiometry data saved successfully");
      } catch (error) {
        console.error("Save failed", error);
      }
    })();
  }, [currentStoichiometry.id, isBusy, onSave, save, setCurrentStoichiometry, trackEvent]);

  const handleDelete = React.useCallback(async () => {
    if (isBusy) {
      return;
    }

    const shouldDelete = await confirm(
      "Delete Stoichiometry Table",
      "Are you sure you want to delete this stoichiometry table? This action cannot be undone.",
      "Delete",
      "Cancel",
    );

    if (!shouldDelete) {
      return;
    }

    trackEvent("user:delete:stoichiometry_table:document_editor");
    try {
      await deleteTable();
      onDelete?.();
      setCurrentStoichiometry(null);
    } catch (error) {
      console.error("Delete failed", error);
    }
  }, [confirm, deleteTable, isBusy, onDelete, setCurrentStoichiometry, trackEvent]);

  return (
    <StoichiometryTableControllerProvider value={tableControllerWithRefresh}>
      <DialogContent>
        <Stack spacing={2} flexWrap="nowrap">
          <Box>
            <Typography variant="body2">
              Double-click to edit Equivalent, Mass, Moles, Actual Mass,
              Actual Moles, or Notes. Yield/Excess values are calculated
              automatically, as are each pairing of moles and mass.
            </Typography>
            <StoichiometryTable
              editable
              stoichiometryId={currentStoichiometry.id}
              stoichiometryRevision={currentStoichiometry.revision}
              hasChanges={hasChanges}
            />
          </Box>
        </Stack>
      </DialogContent>
      <DialogActions>
        {hasChanges && (
          <ValidatingSubmitButton
            onClick={handleSave}
            loading={isSaving}
            validationResult={IsValid()}
          >
            Save Changes
          </ValidatingSubmitButton>
        )}
        <Button
          onClick={() => {
            void handleDelete();
          }}
          variant="outlined"
          color="error"
          disabled={isBusy}
        >
          Delete
        </Button>
        <Button
          disabled={isBusy}
          onClick={() => {
            void handleClose();
          }}
        >
          Close
        </Button>
      </DialogActions>
    </StoichiometryTableControllerProvider>
  );
};

const StandaloneDialogInner = ({
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
}) => {
  const titleId = React.useId();
  const { getToken } = useOauthToken();
  const {
    mutate: mutateCalculateStoichiometry,
    reset: resetCalculateStoichiometry,
    isPending: isRequestInFlight,
    isError: hasCalculateError,
    error: calculateStoichiometryError,
  } = useCalculateStoichiometryMutation({ getToken });
  const { addAlert } = React.useContext(AlertContext);
  const { trackEvent } = React.useContext(AnalyticsContext);
  const [currentStoichiometry, setCurrentStoichiometry] = React.useState<{
    id: number;
    revision: number;
  } | null>(
    stoichiometryId !== undefined && stoichiometryRevision !== undefined
      ? { id: stoichiometryId, revision: stoichiometryRevision }
      : null,
  );
  const [actuallyOpen, setActuallyOpen] = React.useState(false);
  const tableCloseHandlerRef = React.useRef<(() => Promise<void>) | null>(null);
  const chemistryStatus = useIntegrationIsAllowedAndEnabled("CHEMISTRY");
  const syncedStoichiometry = React.useMemo(
    () =>
      stoichiometryId !== undefined && stoichiometryRevision !== undefined
        ? { id: stoichiometryId, revision: stoichiometryRevision }
        : null,
    [stoichiometryId, stoichiometryRevision],
  );

  const registerCloseHandler = React.useCallback(
    (handler: (() => Promise<void>) | null) => {
      tableCloseHandlerRef.current = handler;
    },
    [],
  );

  React.useEffect(() => {
    if (open) {
      resetCalculateStoichiometry();
    }
  }, [open, resetCalculateStoichiometry]);

  React.useEffect(() => {
    if (!open) {
      tableCloseHandlerRef.current = null;
      setActuallyOpen(false);
      return;
    }

    setCurrentStoichiometry((previousStoichiometry) =>
      areSameStoichiometry(previousStoichiometry, syncedStoichiometry)
        ? previousStoichiometry
        : syncedStoichiometry,
    );
  }, [open, syncedStoichiometry]);

  React.useEffect(() => {
    if (!open) {
      return;
    }

    FetchingData.match(chemistryStatus, {
      loading: () => {
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
  }, [
    open,
    chemistryStatus,
    addAlert,
  ]);

  const handleCalculate = React.useCallback(() => {
    if (isRequestInFlight) {
      return;
    }

    resetCalculateStoichiometry();
    trackEvent("user:create:stoichiometry_table:document_editor");

    mutateCalculateStoichiometry(
      {
        chemId: chemId ?? undefined,
        recordId,
      },
      {
        onSuccess: ({ id, revision }) => {
          setCurrentStoichiometry({ id, revision });
          onTableCreated?.(id, revision);
        }
      },
    );
  }, [
    mutateCalculateStoichiometry,
    chemId,
    isRequestInFlight,
    onTableCreated,
    recordId,
    resetCalculateStoichiometry,
    trackEvent,
  ]);

  const handleCloseWithoutTable = React.useCallback(() => {
    if (isRequestInFlight) {
      return;
    }

    resetCalculateStoichiometry();
    onClose();
  }, [isRequestInFlight, onClose, resetCalculateStoichiometry]);

  return (
    <Dialog
      open={actuallyOpen}
      disableEscapeKeyDown={isRequestInFlight}
      onClose={(_event, _reason) => {
        if (currentStoichiometry !== null) {
          void tableCloseHandlerRef.current?.();
          return;
        }
        handleCloseWithoutTable();
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
      {actuallyOpen && currentStoichiometry === null && (
        <>
          <DialogContent>
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
                {isRequestInFlight
                  ? "Calculating..."
                  : "Calculate Stoichiometry"}
              </Button>
              {hasCalculateError && calculateStoichiometryError && (
                <Alert severity="error" sx={{ width: "100%", maxWidth: 480 }}>
                  {calculateStoichiometryError.message}
                </Alert>
              )}
            </Box>
          </DialogContent>
          <DialogActions>
            <Button
              disabled={isRequestInFlight}
              onClick={handleCloseWithoutTable}
            >
              Close
            </Button>
          </DialogActions>
        </>
      )}
      {actuallyOpen && currentStoichiometry !== null && (
        <React.Suspense
          fallback={
            <StoichiometryTableLoadingFallback disableClose={isRequestInFlight} />
          }
        >
          <EditableStoichiometryDialogSection
            currentStoichiometry={currentStoichiometry}
            onClose={onClose}
            onSave={onSave}
            onDelete={onDelete}
            setCurrentStoichiometry={setCurrentStoichiometry}
            registerCloseHandler={registerCloseHandler}
          />
        </React.Suspense>
      )}
    </Dialog>
  );
};

const StoichiometryDialog = (
  props: React.ComponentProps<typeof StandaloneDialogInner>,
) => {
  return (
    <QueryClientProvider client={queryClient}>
      <DialogBoundary>
        <ConfirmProvider>
          <StandaloneDialogInner {...props} />
        </ConfirmProvider>
      </DialogBoundary>
    </QueryClientProvider>
  );
};

export default StoichiometryDialog;

