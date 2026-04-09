import React from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Typography from "@mui/material/Typography";
import { Dialog } from "@/components/DialogBoundary";
import { useIntegrationIsAllowedAndEnabled } from "@/hooks/api/integrationHelpers";
import useOauthToken from "@/hooks/auth/useOauthToken";
import { useCalculateStoichiometryMutation } from "@/modules/stoichiometry/mutations";
import AlertContext, { mkAlert } from "@/stores/contexts/Alert";
import AnalyticsContext from "@/stores/contexts/Analytics";
import AppBar from "../../../components/AppBar";
import * as FetchingData from "../../../util/fetchingData";
import EditableStoichiometryDialogSection from "./EditableStoichiometryDialogSection";
import {
  type CurrentStoichiometry,
  type RegisterCloseHandler,
  STOICHIOMETRY_DIALOG_ACTION_BUTTON_SX,
} from "./shared";
import CircularProgress from "@mui/material/CircularProgress";

export type StandaloneDialogInnerProps = {
  open: boolean;
  onClose: () => void;
  chemId: number | null;
  recordId: number;
  stoichiometryId: number | undefined;
  stoichiometryRevision: number | undefined;
  onTableCreated?: (id: number, version: number) => void;
  onSave?: (id: number, version: number) => void;
  onDelete?: () => void;
};

function areSameStoichiometry(
  left: CurrentStoichiometry | null,
  right: CurrentStoichiometry | null,
) {
  return left?.id === right?.id && left?.revision === right?.revision;
}

export default function StoichiometryDialog({
  open,
  onClose,
  chemId,
  recordId,
  stoichiometryId,
  stoichiometryRevision,
  onTableCreated,
  onSave,
  onDelete,
}: StandaloneDialogInnerProps): React.ReactNode {
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
  const [currentStoichiometry, setCurrentStoichiometry] =
    React.useState<CurrentStoichiometry | null>(
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

  const registerCloseHandler = React.useCallback<RegisterCloseHandler>(
    (handler) => {
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
  }, [open, chemistryStatus, addAlert]);

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
        },
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
              sx={STOICHIOMETRY_DIALOG_ACTION_BUTTON_SX}
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
                <Button
                  disabled={isRequestInFlight}
                  sx={STOICHIOMETRY_DIALOG_ACTION_BUTTON_SX}
                >
                  Delete
                </Button>
                <Button
                  disabled={isRequestInFlight}
                  sx={STOICHIOMETRY_DIALOG_ACTION_BUTTON_SX}
                >
                  Close
                </Button>
              </DialogActions>
            </>
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
}
