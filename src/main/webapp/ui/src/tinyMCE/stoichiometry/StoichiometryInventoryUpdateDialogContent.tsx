import WarningAmberIcon from "@mui/icons-material/WarningAmber";
import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import { useMutation } from "@tanstack/react-query";
import React from "react";
import { useTranslation } from "react-i18next";
import type { InventoryQuantityQueryResult } from "@/modules/inventory/queries";
import { resolveStoichiometryErrorMessage } from "@/modules/stoichiometry/utils";
import StoichiometryInventoryUpdateMoleculeRow from "@/tinyMCE/stoichiometry/StoichiometryInventoryUpdateMoleculeRow";
import type { EditableMolecule } from "@/tinyMCE/stoichiometry/types";
import { getInventoryUpdateEligibility } from "@/tinyMCE/stoichiometry/utils";

export type InventoryStockUpdateResult = {
  refreshedStoichiometry?: {
    id: number;
    revision: number;
  };
  results: ReadonlyArray<{
    moleculeId: number;
    moleculeName: string;
    success: boolean;
    errorMessage: string | null;
  }>;
};

type StoichiometryInventoryUpdateDialogContentProps = {
  titleId: string;
  molecules: ReadonlyArray<EditableMolecule>;
  linkedInventoryQuantityInfoByGlobalId: ReadonlyMap<string, InventoryQuantityQueryResult>;
  onSave?: (selectedMoleculeIds: number[]) => Promise<InventoryStockUpdateResult>;
  onClose: () => void;
};

function getDefaultValues(
  molecules: ReadonlyArray<EditableMolecule>,
  linkedInventoryQuantityInfoByGlobalId: ReadonlyMap<string, InventoryQuantityQueryResult>,
): number[] {
  return molecules
    .filter((molecule) => {
      const eligibility = getInventoryUpdateEligibility(molecule, linkedInventoryQuantityInfoByGlobalId);
      return eligibility.disabledReason === null && molecule.inventoryLink?.stockDeducted !== true;
    })
    .map(({ id }) => id);
}

/**
 * The stateful body of the Update Inventory Stock dialog. It is only mounted
 * while the dialog is open (MUI's `Dialog` unmounts its children when
 * closed), so each open session starts from a fresh mount: the default
 * selection is computed in the `useState` initializer and all state is
 * discarded on close, with no reset effects or refs. Correct preselection
 * relies on the toolbar disabling the opening button until every linked
 * molecule's inventory quantity has resolved.
 */
export default function StoichiometryInventoryUpdateDialogContent({
  titleId,
  molecules,
  linkedInventoryQuantityInfoByGlobalId,
  onSave,
  onClose,
}: StoichiometryInventoryUpdateDialogContentProps): React.ReactNode {
  const { t } = useTranslation("common");
  const [selectedMoleculeIds, setSelectedMoleculeIds] = React.useState<number[]>(() =>
    getDefaultValues(molecules, linkedInventoryQuantityInfoByGlobalId),
  );
  const saveMutation = useMutation({
    mutationFn: async (moleculeIds: number[]) => {
      if (!onSave) {
        return {
          results: [],
        } satisfies InventoryStockUpdateResult;
      }
      return onSave(moleculeIds);
    },
  });
  const failedResults = saveMutation.data?.results.filter(({ success }) => !success) ?? [];
  const saveFeedback =
    saveMutation.isSuccess && failedResults.length > 0 ? t("stoichiometry.inventoryUpdate.saveFeedback") : null;
  const saveError = (() => {
    if (saveMutation.isError) {
      const message = resolveStoichiometryErrorMessage(saveMutation.error, t, saveMutation.error.message);
      return t("stoichiometry.inventoryUpdate.saveError", { message });
    }
    if (failedResults.length === 0) {
      return null;
    }
    return (
      Array.from(
        new Set(
          failedResults
            .map(({ errorMessage }) => errorMessage)
            .filter((message): message is string => Boolean(message)),
        ),
      ).join(" ") || null
    );
  })();
  const hasInvalidSelectedRows = selectedMoleculeIds.some((selectedMoleculeId) => {
    const selectedMolecule = molecules.find(({ id }) => id === selectedMoleculeId);
    if (!selectedMolecule) {
      return true;
    }
    return (
      getInventoryUpdateEligibility(selectedMolecule, linkedInventoryQuantityInfoByGlobalId).disabledReason !== null
    );
  });
  const selectionError = hasInvalidSelectedRows ? t("stoichiometry.inventoryUpdate.selectionError") : null;
  const handleSubmit = React.useCallback(async () => {
    if (selectedMoleculeIds.length === 0) {
      saveMutation.reset();
      return;
    }
    if (hasInvalidSelectedRows) {
      saveMutation.reset();
      return;
    }
    if (!onSave) {
      onClose();
      return;
    }
    saveMutation.reset();
    try {
      const result = await saveMutation.mutateAsync(selectedMoleculeIds);
      const hasFailures = result.results.some(({ success }) => !success);
      if (!hasFailures) {
        onClose();
        return;
      }
      setSelectedMoleculeIds([]);
    } catch {
      setSelectedMoleculeIds([]);
    }
  }, [onClose, hasInvalidSelectedRows, onSave, saveMutation, selectedMoleculeIds]);
  return (
    <Box
      component="form"
      sx={{
        display: "contents",
      }}
      onSubmit={(event) => {
        event.preventDefault();
        event.stopPropagation();
        void handleSubmit();
      }}
    >
      <DialogTitle id={titleId}>{t("stoichiometry.inventoryUpdate.dialogTitle")}</DialogTitle>
      <DialogContent>
        <Stack spacing={2}>
          <Typography variant="body2" color="text.secondary">
            {t("stoichiometry.inventoryUpdate.selectMolecules")}
          </Typography>
          <Alert
            severity="warning"
            icon={
              <WarningAmberIcon
                aria-label={t("stoichiometry.inventoryUpdate.irreversibleWarningLabel")}
                fontSize="small"
                sx={{
                  color: "warning.main",
                }}
              />
            }
          >
            <AlertTitle>{t("stoichiometry.inventoryUpdate.irreversibleTitle")}</AlertTitle>

            <Typography variant="body2" gutterBottom>
              <strong>{t("stoichiometry.inventoryUpdate.permanentlyReduceWarning")}</strong>
            </Typography>
            <Typography variant="body2" gutterBottom>
              {t("stoichiometry.inventoryUpdate.cannotReplenish")}
            </Typography>
            <Typography variant="body2">{t("stoichiometry.inventoryUpdate.proceedIfUsed")}</Typography>
          </Alert>
          {selectionError && <Alert severity="warning">{selectionError}</Alert>}
          {saveFeedback && <Alert severity="info">{saveFeedback}</Alert>}
          {saveError && <Alert severity="error">{saveError}</Alert>}
          <TableContainer
            component={Paper}
            variant="outlined"
            sx={{
              maxHeight: 560,
              overflowY: "auto",
              borderRadius: 1,
            }}
          >
            <Table stickyHeader size="small">
              <TableHead>
                <TableRow>
                  <TableCell
                    padding="checkbox"
                    aria-label={t("stoichiometry.inventoryUpdate.selectMoleculeLabel")}
                    width={52}
                    sx={{
                      px: 0.5,
                    }}
                  />
                  <TableCell width="55%">{t("stoichiometry.inventoryUpdate.molecule")}</TableCell>
                  <TableCell align="right" width="15%">
                    {t("stoichiometry.inventoryUpdate.inStock")}
                  </TableCell>
                  <TableCell align="right" width="15%">
                    {t("stoichiometry.inventoryUpdate.willUse")}
                  </TableCell>
                  <TableCell align="right" width="15%">
                    {t("stoichiometry.inventoryUpdate.remaining")}
                  </TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {molecules.map((molecule) => {
                  const eligibility = getInventoryUpdateEligibility(molecule, linkedInventoryQuantityInfoByGlobalId);
                  const { disabledReason, helperText, stockDisplay } = eligibility;
                  const disabled = disabledReason !== null;
                  const selected = !disabled && selectedMoleculeIds.includes(molecule.id);
                  return (
                    <StoichiometryInventoryUpdateMoleculeRow
                      key={molecule.id}
                      molecule={molecule}
                      selected={selected}
                      disabled={disabled}
                      helperText={helperText}
                      stockDisplay={stockDisplay}
                      onToggle={() => {
                        if (disabled) {
                          return;
                        }
                        saveMutation.reset();
                        setSelectedMoleculeIds((currentIds) =>
                          selected ? currentIds.filter((id) => id !== molecule.id) : [...currentIds, molecule.id],
                        );
                      }}
                    />
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button type="button" onClick={onClose}>
          {t("actions.cancel")}
        </Button>
        <Button
          type="submit"
          variant="contained"
          color="callToAction"
          disabled={selectedMoleculeIds.length === 0 || hasInvalidSelectedRows || saveMutation.isPending}
        >
          {saveMutation.isPending ? t("stoichiometry.inventoryUpdate.saving") : t("actions.save")}
        </Button>
      </DialogActions>
    </Box>
  );
}
