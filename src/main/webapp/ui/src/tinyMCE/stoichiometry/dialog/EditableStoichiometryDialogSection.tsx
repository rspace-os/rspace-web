import React from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { useConfirm } from "@/components/ConfirmProvider";
import AnalyticsContext from "@/stores/contexts/Analytics";
import ValidatingSubmitButton, {
  IsValid,
} from "../../../components/ValidatingSubmitButton";
import StoichiometryTable from "../StoichiometryTable";
import { StoichiometryTableControllerProvider } from "../StoichiometryTableControllerContext";
import {
  type RefreshedStoichiometry,
  useEditableStoichiometryTable,
} from "../useEditableStoichiometryTable";
import {
  type CurrentStoichiometry,
  type RegisterCloseHandler,
  type SetCurrentStoichiometry,
  STOICHIOMETRY_DIALOG_ACTION_BUTTON_SX,
} from "./shared";

export type EditableStoichiometryDialogSectionProps = {
  currentStoichiometry: CurrentStoichiometry;
  onClose: () => void;
  onSave?: (id: number, version: number) => void;
  onDelete?: () => void;
  setCurrentStoichiometry: SetCurrentStoichiometry;
  registerCloseHandler?: RegisterCloseHandler;
};

export default function EditableStoichiometryDialogSection({
  currentStoichiometry,
  onClose,
  onSave,
  onDelete,
  setCurrentStoichiometry,
  registerCloseHandler,
}: EditableStoichiometryDialogSectionProps): React.ReactNode {
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
    onStoichiometryRefreshed: (
      refreshedStoichiometry: RefreshedStoichiometry,
    ) => {
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
  }, [
    currentStoichiometry.id,
    isBusy,
    onSave,
    save,
    setCurrentStoichiometry,
    trackEvent,
  ]);

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
    <StoichiometryTableControllerProvider value={tableController}>
      <DialogContent>
        <Stack spacing={2} flexWrap="nowrap">
          <Box>
            <Typography variant="body2">
              Double-click to edit Equivalent, Mass, Moles, Actual Mass, Actual
              Moles, or Notes. Yield/Excess values are calculated automatically,
              as are each pairing of moles and mass.
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
            sx={STOICHIOMETRY_DIALOG_ACTION_BUTTON_SX}
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
          sx={STOICHIOMETRY_DIALOG_ACTION_BUTTON_SX}
        >
          Delete
        </Button>
        <Button
          disabled={isBusy}
          sx={STOICHIOMETRY_DIALOG_ACTION_BUTTON_SX}
          onClick={() => {
            void handleClose();
          }}
        >
          Close
        </Button>
      </DialogActions>
    </StoichiometryTableControllerProvider>
  );
}
