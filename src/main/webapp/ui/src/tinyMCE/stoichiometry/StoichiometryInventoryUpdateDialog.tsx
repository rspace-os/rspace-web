import React from "react";
import { Dialog } from "@/components/DialogBoundary";
import type { InventoryQuantityQueryResult } from "@/modules/inventory/queries";
import StoichiometryInventoryUpdateDialogContent, {
  type InventoryStockUpdateResult,
} from "@/tinyMCE/stoichiometry/StoichiometryInventoryUpdateDialogContent";
import type { EditableMolecule } from "@/tinyMCE/stoichiometry/types";

export type { InventoryStockUpdateResult };

type StoichiometryInventoryUpdateDialogProps = {
  open: boolean;
  molecules: ReadonlyArray<EditableMolecule>;
  linkedInventoryQuantityInfoByGlobalId: ReadonlyMap<string, InventoryQuantityQueryResult>;
  onSave?: (selectedMoleculeIds: number[]) => Promise<InventoryStockUpdateResult>;
  onClose: () => void;
};

/**
 * Thin shell around the Update Inventory Stock dialog. All state lives in the
 * content component, which MUI mounts fresh on each open and unmounts on
 * close, so opening the dialog is what resets it.
 */
export default function StoichiometryInventoryUpdateDialog({
  open,
  molecules,
  linkedInventoryQuantityInfoByGlobalId,
  onSave,
  onClose,
}: StoichiometryInventoryUpdateDialogProps): React.ReactNode {
  const titleId = React.useId();
  return (
    <Dialog
      open={open}
      onClose={onClose}
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
      <StoichiometryInventoryUpdateDialogContent
        titleId={titleId}
        molecules={molecules}
        linkedInventoryQuantityInfoByGlobalId={linkedInventoryQuantityInfoByGlobalId}
        onSave={onSave}
        onClose={onClose}
      />
    </Dialog>
  );
}
