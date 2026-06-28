import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import React from "react";
import { useTranslation } from "react-i18next";
import { useConfirm } from "@/components/ConfirmProvider";
import AnalyticsContext from "@/stores/contexts/Analytics";
import ValidatingSubmitButton, { IsValid } from "../../../components/ValidatingSubmitButton";
import StoichiometryTable from "../StoichiometryTable";
import { StoichiometryTableControllerProvider } from "../StoichiometryTableControllerContext";
import { type RefreshedStoichiometry, useEditableStoichiometryTable } from "../useEditableStoichiometryTable";
import {
  type CurrentStoichiometry,
  type RegisterCloseHandler,
  type SetCurrentStoichiometry,
  STOICHIOMETRY_DIALOG_ACTION_BUTTON_SX,
} from "./shared";

function getMutationErrorMessage(error: unknown, fallbackMessage: string): string {
  return error instanceof Error ? error.message : fallbackMessage;
}

export default function EditableStoichiometryDialogSection({
  currentStoichiometry,
  chemId,
  onClose,
  onSave,
  onDelete,
  setCurrentStoichiometry,
  registerCloseHandler,
}: {
  currentStoichiometry: CurrentStoichiometry;
  chemId: number | null;
  onClose: () => void;
  onSave?: (id: number, version: number) => void;
  onDelete?: () => void;
  setCurrentStoichiometry: SetCurrentStoichiometry;
  registerCloseHandler?: RegisterCloseHandler;
}): React.ReactNode {
  const { t } = useTranslation("common");
  const { trackEvent } = React.useContext(AnalyticsContext);
  const confirm = useConfirm();
  const [mutationError, setMutationError] = React.useState<string | null>(null);
  const { hasChanges, isBusy, isSaving, save, deleteTable, tableController } = useEditableStoichiometryTable({
    stoichiometryId: currentStoichiometry.id,
    stoichiometryRevision: currentStoichiometry.revision,
    activeChemId: chemId,
    onStoichiometryRefreshed: (refreshedStoichiometry: RefreshedStoichiometry) => {
      setCurrentStoichiometry(refreshedStoichiometry);
      onSave?.(refreshedStoichiometry.id, refreshedStoichiometry.revision);
    },
  });

  const handleClose = React.useCallback(async () => {
    if (isBusy) {
      return;
    }
    if (
      hasChanges &&
      !(await confirm(
        t("stoichiometry.dialog.discardChangesTitle"),
        t("stoichiometry.dialog.discardChangesMessage"),
        t("stoichiometry.dialog.discardChangesConfirm"),
        t("actions.cancel"),
      ))
    ) {
      return;
    }
    onClose();
  }, [confirm, hasChanges, isBusy, onClose, t]);

  React.useEffect(() => {
    registerCloseHandler?.(handleClose);
    return () => {
      registerCloseHandler?.(null);
    };
  }, [handleClose, registerCloseHandler]);

  const handleSave = () => {
    if (isBusy) {
      return;
    }
    trackEvent("user:save:stoichiometry_table:document_editor");
    void (async () => {
      setMutationError(null);
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
        setMutationError(getMutationErrorMessage(error, t("stoichiometry.dialog.saveError")));
        console.error("Save failed", error);
      }
    })();
  };

  const handleDelete = async () => {
    if (isBusy) {
      return;
    }

    const shouldDelete = await confirm(
      t("stoichiometry.dialog.deleteTitle"),
      t("stoichiometry.dialog.deleteMessage"),
      t("actions.delete"),
      t("actions.cancel"),
    );

    if (!shouldDelete) {
      return;
    }

    trackEvent("user:delete:stoichiometry_table:document_editor");
    setMutationError(null);
    try {
      await deleteTable();
      onDelete?.();
      setCurrentStoichiometry(null);
    } catch (error) {
      setMutationError(getMutationErrorMessage(error, t("stoichiometry.dialog.deleteError")));
      console.error("Delete failed", error);
    }
  };

  return (
    <StoichiometryTableControllerProvider value={tableController}>
      <DialogContent>
        <Stack spacing={2} sx={{ flexWrap: "nowrap" }}>
          <Box>
            <Typography variant="body2">{t("stoichiometry.dialog.editInstructions")}</Typography>
            <StoichiometryTable
              editable
              stoichiometryId={currentStoichiometry.id}
              stoichiometryRevision={currentStoichiometry.revision}
              hasChanges={hasChanges}
              activeChemId={chemId}
            />
          </Box>
          {mutationError && <Alert severity="error">{mutationError}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        {hasChanges && (
          <ValidatingSubmitButton
            onClick={handleSave}
            loading={isSaving}
            validationResult={IsValid()}
            sx={STOICHIOMETRY_DIALOG_ACTION_BUTTON_SX}
          >
            {t("stoichiometry.dialog.saveChanges")}
          </ValidatingSubmitButton>
        )}
        <Button
          onClick={() => {
            void handleDelete();
          }}
          variant="outlined"
          color="error"
          disabled={isBusy}
          sx={STOICHIOMETRY_DIALOG_ACTION_BUTTON_SX}
        >
          {t("actions.delete")}
        </Button>
        <Button
          disabled={isBusy}
          sx={STOICHIOMETRY_DIALOG_ACTION_BUTTON_SX}
          onClick={() => {
            void handleClose();
          }}
        >
          {t("actions.close")}
        </Button>
      </DialogActions>
    </StoichiometryTableControllerProvider>
  );
}
