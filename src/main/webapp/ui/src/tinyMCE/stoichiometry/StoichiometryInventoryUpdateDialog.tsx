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
import { Dialog } from "@/components/DialogBoundary";
import type { InventoryQuantityQueryResult } from "@/modules/inventory/queries";
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
type StoichiometryInventoryUpdateDialogProps = {
  open: boolean;
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
export default function StoichiometryInventoryUpdateDialog({
  open,
  molecules,
  linkedInventoryQuantityInfoByGlobalId,
  onSave,
  onClose,
}: StoichiometryInventoryUpdateDialogProps): React.ReactNode {
  const titleId = React.useId();
  const { t } = useTranslation("common");
  const wasOpenRef = React.useRef(false);
  const [selectedMoleculeIds, setSelectedMoleculeIds] = React.useState<number[]>([]);
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
      const message = saveMutation.error.message;
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
  const resetDialogState = React.useCallback(() => {
    setSelectedMoleculeIds(getDefaultValues(molecules, linkedInventoryQuantityInfoByGlobalId));
    saveMutation.reset();
  }, [linkedInventoryQuantityInfoByGlobalId, molecules, saveMutation]);
  React.useEffect(() => {
    if (open && !wasOpenRef.current) {
      resetDialogState();
    }
    wasOpenRef.current = open;
  }, [open, resetDialogState]);
  const handleClose = React.useCallback(() => {
    resetDialogState();
    onClose();
  }, [onClose, resetDialogState]);
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
      handleClose();
      return;
    }
    saveMutation.reset();
    try {
      const result = await saveMutation.mutateAsync(selectedMoleculeIds);
      const hasFailures = result.results.some(({ success }) => !success);
      if (!hasFailures) {
        handleClose();
        return;
      }
      setSelectedMoleculeIds([]);
    } catch {
      setSelectedMoleculeIds([]);
    }
  }, [handleClose, hasInvalidSelectedRows, onSave, saveMutation, selectedMoleculeIds]);
  return (
    <Dialog
      open={open}
      onClose={handleClose}
      maxWidth="lg"
      fullWidth
      aria-labelledby={titleId}
      slotProps={{
        paper: {
          sx: {
            minWidth: (theme) => theme.breakpoints.values.sm,
          },
        },
      }}
    >
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
                  aria-label={t("stoichiometry.inventoryUpdate.irreversibleWarningAria")}
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
                      aria-label={t("stoichiometry.inventoryUpdate.selectMoleculeAria")}
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
          <Button type="button" onClick={handleClose}>
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
    </Dialog>
  );
}
