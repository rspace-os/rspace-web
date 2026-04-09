import React from "react";
import { useMutation } from "@tanstack/react-query";
import Alert from "@mui/material/Alert";
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
import { Dialog } from "@/components/DialogBoundary";
import type { InventoryQuantityQueryResult } from "@/modules/inventory/queries";
import StoichiometryInventoryUpdateMoleculeRow from "@/tinyMCE/stoichiometry/StoichiometryInventoryUpdateMoleculeRow";
import type { EditableMolecule } from "@/tinyMCE/stoichiometry/types";
import { getInventoryUpdateEligibility } from "@/tinyMCE/stoichiometry/utils";
import WarningAmberIcon from "@mui/icons-material/WarningAmber";
import AlertTitle from "@mui/material/AlertTitle";
import ListItem from "@mui/material/ListItem";
import List from "@mui/material/List";
import Box from "@mui/material/Box";

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
  linkedInventoryQuantityInfoByGlobalId: ReadonlyMap<
    string,
    InventoryQuantityQueryResult
  >;
  onSave?: (selectedMoleculeIds: number[]) => Promise<InventoryStockUpdateResult>;
  onClose: () => void;
};

function getDefaultValues(
  molecules: ReadonlyArray<EditableMolecule>,
  linkedInventoryQuantityInfoByGlobalId: ReadonlyMap<
    string,
    InventoryQuantityQueryResult
  >,
): number[] {
  return molecules
    .filter(
      (molecule) =>
        getInventoryUpdateEligibility(
          molecule,
          linkedInventoryQuantityInfoByGlobalId,
        ).disabledReason === null,
    )
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
  const wasOpenRef = React.useRef(false);
  const [selectedMoleculeIds, setSelectedMoleculeIds] = React.useState<number[]>([]);
  const [selectionError, setSelectionError] = React.useState<string | null>(null);
  const saveMutation = useMutation({
    mutationFn: async (moleculeIds: number[]) => {
      if (!onSave) {
        return { results: [] } satisfies InventoryStockUpdateResult;
      }
      return onSave(moleculeIds);
    },
  });

  const failedResults = React.useMemo(
    () => saveMutation.data?.results.filter(({ success }) => !success) ?? [],
    [saveMutation.data],
  );
  const saveFeedback =
    saveMutation.isSuccess && failedResults.length > 0
      ? "Current stock amounts were refreshed. Re-select any remaining molecules to retry."
      : null;
  const saveError = React.useMemo(() => {
    if (saveMutation.isError) {
      const message = saveMutation.error.message;
      return `${message} Current stock amounts were refreshed where possible.`;
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
  }, [failedResults, saveMutation.error, saveMutation.isError]);

  const resetDialogState = React.useCallback(() => {
    setSelectedMoleculeIds(
      getDefaultValues(molecules, linkedInventoryQuantityInfoByGlobalId),
    );
    setSelectionError(null);
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
      setSelectionError("Select at least one molecule.");
      saveMutation.reset();
      return;
    }

    if (!onSave) {
      handleClose();
      return;
    }

    setSelectionError(null);
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
  }, [handleClose, onSave, saveMutation, selectedMoleculeIds]);

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      maxWidth="lg"
      fullWidth
      PaperProps={{
        sx: {
          minWidth: (theme) => theme.breakpoints.values.sm,
        },
      }}
      aria-labelledby={titleId}
    >
      <form
        style={{ display: "contents" }}
        onSubmit={(event) => {
          event.preventDefault();
          event.stopPropagation();
          void handleSubmit();
        }}
      >
        <DialogTitle id={titleId}>Update Inventory Stock</DialogTitle>
        <DialogContent>
          <Stack spacing={2}>
            <Typography variant="body2" color="text.secondary">
              Select the molecules from this stoichiometry table whose linked
              inventory stock should be updated.
            </Typography>
            <Alert
              severity="warning"
              icon={
                <WarningAmberIcon
                  aria-label="Action irreversible warning"
                  fontSize="small"
                  sx={{ color: "warning.main" }}
                />
              }
            >
              <AlertTitle>WARNING: This action is irreversible</AlertTitle>
              <Box>
                <strong>
                  This will permanently reduce inventory quantities.
                </strong>{" "}
                Stock cannot be automatically replenished if you:
                <List dense disablePadding sx={{ listStyle: "disc inside" }}>
                  <ListItem sx={{ display: "list-item", lineHeight: 1 }}>
                    Change quantities later
                  </ListItem>
                  <ListItem sx={{ display: "list-item", lineHeight: 1 }}>
                    Delete this stoichiometry table
                  </ListItem>
                  <ListItem sx={{ display: "list-item", lineHeight: 1 }}>
                    Delete the document
                  </ListItem>
                  <ListItem sx={{ display: "list-item", lineHeight: 1 }}>
                    Unlink samples
                  </ListItem>
                </List>
                Only proceed if you have actually used these materials in your
                experiment.
              </Box>
            </Alert>
            {selectionError && (
              <Alert severity="warning">{selectionError}</Alert>
            )}
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
              <Table
                stickyHeader
                size="small"
              >
                <TableHead>
                  <TableRow>
                    <TableCell
                      padding="checkbox"
                      aria-label="Select molecule"
                      width={52}
                      sx={{ px: 0.5 }}
                    />
                    <TableCell width="60%">Molecule</TableCell>
                    <TableCell align="right" width="15%">
                      In Stock
                    </TableCell>
                    <TableCell align="right" width="15%">
                      Will Use
                    </TableCell>
                    <TableCell align="right" width="14%">
                      Remaining
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {molecules.map((molecule) => {
                    const eligibility = getInventoryUpdateEligibility(
                      molecule,
                      linkedInventoryQuantityInfoByGlobalId,
                    );
                    const { disabledReason, helperText, stockDisplay } =
                      eligibility;
                    const disabled = disabledReason !== null;
                    const selected =
                      !disabled && selectedMoleculeIds.includes(molecule.id);

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

                          setSelectionError(null);
                          saveMutation.reset();
                          setSelectedMoleculeIds((currentIds) =>
                            selected
                              ? currentIds.filter((id) => id !== molecule.id)
                              : [...currentIds, molecule.id],
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
            Cancel
          </Button>
          <Button
            type="submit"
            variant="contained"
            color="callToAction"
            disabled={
              selectedMoleculeIds.length === 0 || saveMutation.isPending
            }
          >
            {saveMutation.isPending ? "Saving..." : "Save"}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
