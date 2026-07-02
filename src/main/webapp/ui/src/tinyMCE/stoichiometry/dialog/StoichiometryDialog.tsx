import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Typography from "@mui/material/Typography";
import React from "react";
import { useTranslation } from "react-i18next";
import { Dialog } from "@/components/DialogBoundary";
import { useIntegrationIsAllowedAndEnabled } from "@/hooks/api/integrationHelpers";
import useOauthToken from "@/hooks/auth/useOauthToken";
import { useCalculateStoichiometryMutation } from "@/modules/stoichiometry/mutations";
import AlertContext, { mkAlert } from "@/stores/contexts/Alert";
import AnalyticsContext from "@/stores/contexts/Analytics";
import AppBar from "../../../components/AppBar";
import * as FetchingData from "../../../util/fetchingData";
import EditableStoichiometryDialogSection from "./EditableStoichiometryDialogSection";
import { type CurrentStoichiometry, type RegisterCloseHandler, STOICHIOMETRY_DIALOG_ACTION_BUTTON_SX } from "./shared";

export type StandaloneDialogInnerProps = {
  open: boolean;
  onClose: () => void;
  chemId: number | null;
  autoCreateTableOnOpen?: boolean;
  recordId: number;
  stoichiometryId: number | undefined;
  stoichiometryRevision: number | undefined;
  onTableCreated?: (id: number, version: number) => void;
  onSave?: (id: number, version: number) => void;
  onDelete?: () => void;
};

function areSameStoichiometry(left: CurrentStoichiometry | null, right: CurrentStoichiometry | null) {
  return left?.id === right?.id && left?.revision === right?.revision;
}

export default function StoichiometryDialog({
  open,
  onClose,
  chemId,
  autoCreateTableOnOpen = false,
  recordId,
  stoichiometryId,
  stoichiometryRevision,
  onTableCreated,
  onSave,
  onDelete,
}: StandaloneDialogInnerProps): React.ReactNode {
  const titleId = React.useId();
  const { t } = useTranslation("common");
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
  const [currentStoichiometry, setCurrentStoichiometry] = React.useState<CurrentStoichiometry | null>(
    stoichiometryId !== undefined ? { id: stoichiometryId, revision: stoichiometryRevision } : null,
  );
  const [actuallyOpen, setActuallyOpen] = React.useState(false);
  const tableCloseHandlerRef = React.useRef<(() => Promise<void>) | null>(null);
  const chemistryStatus = useIntegrationIsAllowedAndEnabled("CHEMISTRY");
  const syncedStoichiometry =
    stoichiometryId !== undefined ? { id: stoichiometryId, revision: stoichiometryRevision } : null;

  const registerCloseHandler = React.useCallback<RegisterCloseHandler>((handler) => {
    tableCloseHandlerRef.current = handler;
  }, []);

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
      areSameStoichiometry(previousStoichiometry, syncedStoichiometry) ? previousStoichiometry : syncedStoichiometry,
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
            title: t("stoichiometry.dialog.chemistryStatusErrorTitle"),
            message: t("previewInfo.chemistryStatus.error", { ns: "apps", error }),
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
              title: t("stoichiometry.dialog.chemistryDisabledTitle"),
              message: t("previewInfo.chemistryStatus.disabled", { ns: "apps" }),
            }),
          );
        }
      },
    });
  }, [open, chemistryStatus, addAlert]);

  const handleCalculate = () => {
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
  };

  React.useEffect(() => {
    if (
      actuallyOpen &&
      currentStoichiometry === null &&
      autoCreateTableOnOpen &&
      !isRequestInFlight &&
      !hasCalculateError
    ) {
      handleCalculate();
    }
  }, [
    actuallyOpen,
    currentStoichiometry,
    autoCreateTableOnOpen,
    isRequestInFlight,
    hasCalculateError,
    handleCalculate,
  ]);

  const handleCloseWithoutTable = () => {
    if (isRequestInFlight) {
      return;
    }

    resetCalculateStoichiometry();
    onClose();
  };

  return (
    <Dialog
      open={actuallyOpen}
      onClose={(_event, _reason) => {
        if (isRequestInFlight) return;
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
        currentPage={t("integrations.chemistry.name", { ns: "apps" })}
        accessibilityTips={{
          supportsHighContrastMode: true,
        }}
      />
      <DialogTitle id={titleId} component="h3">
        {t("stoichiometry.dialog.reactionTable")}
      </DialogTitle>
      {actuallyOpen && currentStoichiometry === null && (
        <>
          <DialogContent>
            <Box
              sx={{
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                gap: 2,
                py: 4,
              }}
            >
              <Typography variant="body1" align="center">
                {t("stoichiometry.dialog.calculatePrompt")}
              </Typography>
              <Button variant="contained" color="primary" onClick={handleCalculate} disabled={isRequestInFlight}>
                {isRequestInFlight ? t("stoichiometry.dialog.calculating") : t("stoichiometry.dialog.calculate")}
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
              {t("actions.close")}
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
                  sx={{
                    display: "flex",
                    flexDirection: "column",
                    justifyContent: "center",
                    alignItems: "center",
                    minHeight: 100,
                    my: 2,
                    gap: 1,
                  }}
                >
                  <CircularProgress size={24} aria-label={t("stoichiometry.dialog.loadingTable")} />
                  <Typography variant="body2" color="textSecondary">
                    {t("stoichiometry.dialog.loadingTable")}
                  </Typography>
                </Box>
              </DialogContent>
              <DialogActions>
                <Button disabled={isRequestInFlight} sx={STOICHIOMETRY_DIALOG_ACTION_BUTTON_SX}>
                  {t("actions.delete")}
                </Button>
                <Button disabled={isRequestInFlight} sx={STOICHIOMETRY_DIALOG_ACTION_BUTTON_SX}>
                  {t("actions.close")}
                </Button>
              </DialogActions>
            </>
          }
        >
          <EditableStoichiometryDialogSection
            currentStoichiometry={currentStoichiometry}
            chemId={chemId}
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
