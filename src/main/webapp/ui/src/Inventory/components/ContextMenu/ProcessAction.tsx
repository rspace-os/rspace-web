import { faCodeBranch } from "@fortawesome/free-solid-svg-icons/faCodeBranch";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import type MenuItem from "@mui/material/MenuItem";
import { Observer } from "mobx-react-lite";
import React, { forwardRef } from "react";
import { useTranslation } from "react-i18next";
import type { InventoryRecord } from "@/stores/definitions/InventoryRecord";
import SubSampleModel from "@/stores/models/SubSampleModel";
import OperationWizard from "../Operations/OperationWizard";
import ContextMenuAction, { type ContextMenuRenderOptions } from "./ContextMenuAction";

/**
 * Whether the operation wizard can act on this selection: at least one record, all of them
 * subsamples. The single home for the gate; ContextMenu visibility (ContextActions) and this
 * action's own wizard mounting both use it, so they can never disagree.
 */
export function isProcessableSelection(records: ReadonlyArray<InventoryRecord>): boolean {
  return records.length >= 1 && records.every((r) => r instanceof SubSampleModel);
}

type ProcessActionArgs = {
  as: ContextMenuRenderOptions;
  disabled: string;
  selectedResults: Array<InventoryRecord>;
  closeMenu: () => void;
};

/**
 * Launches the operation wizard on the selected subsamples (RSDEV-1231). ContextActions only shows
 * this entry for a processable selection (isProcessableSelection), so no per-action gating remains
 * here; the wizard's picker then enables single-origin operations for a single selection and Pool
 * for two or more (DevDocs/adr/0007).
 */
const ProcessAction = forwardRef<React.ElementRef<typeof MenuItem>, ProcessActionArgs>(
  ({ as, disabled, selectedResults, closeMenu }, ref) => {
    const { t } = useTranslation("inventory");
    const [open, setOpen] = React.useState(false);
    const origins = selectedResults.filter((r): r is SubSampleModel => r instanceof SubSampleModel);

    const onCloseHandler = () => {
      setOpen(false);
      closeMenu();
    };

    return (
      <Observer>
        {() => (
          <ContextMenuAction
            onClick={() => setOpen(true)}
            // The same code-branch icon the picker shows for Derive (operations_config.json), so the
            // menu entry and the wizard it opens share one visual identity.
            icon={<FontAwesomeIcon icon={faCodeBranch} size="lg" />}
            label={t("operations.action.process")}
            disabledHelp={disabled}
            as={as}
            ref={ref}
          >
            {isProcessableSelection(selectedResults) ? (
              <OperationWizard key={open ? 1 : 0} open={open} onClose={onCloseHandler} origins={origins} />
            ) : null}
          </ContextMenuAction>
        )}
      </Observer>
    );
  },
);

ProcessAction.displayName = "ProcessAction";
export default ProcessAction;
