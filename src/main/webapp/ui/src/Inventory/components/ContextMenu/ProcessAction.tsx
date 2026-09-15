import { faCodeBranch } from "@fortawesome/free-solid-svg-icons/faCodeBranch";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import type MenuItem from "@mui/material/MenuItem";
import { Observer } from "mobx-react-lite";
import React, { forwardRef } from "react";
import { useTranslation } from "react-i18next";
import type { InventoryRecord } from "@/stores/definitions/InventoryRecord";
import SubSampleModel from "@/stores/models/SubSampleModel";
import getRootStore from "@/stores/stores/getRootStore";
import OperationWizard from "../Operations/OperationWizard";
import ContextMenuAction, { type ContextMenuRenderOptions } from "./ContextMenuAction";
import { displayErrorIfAllLocksCouldNotBeAcquired } from "./lockAlerts";

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

    const releaseAll = (records: Array<SubSampleModel>) => Promise.allSettled(records.map((r) => r.releaseLock(true)));

    /**
     * The wizard holds the edit-session lock on every origin for as long as it is open, the same
     * discipline the subsample edit form has (RSDEV-1231). The records do NOT enter edit state:
     * this is a courtesy lock so two users cannot Perform on one origin at once, and the server
     * re-checks it at Perform regardless.
     *
     * A lock held by someone else means the wizard does not open at all, and the locks already
     * taken are given back rather than left to expire.
     */
    const openWithOriginsLocked = async () => {
      const results = await Promise.allSettled(origins.map((o) => o.acquireEditLock()));
      const failure = results.find((r) => r.status === "rejected");
      if (failure) {
        await releaseAll(origins.filter((_, i) => results[i].status === "fulfilled"));
        const shown = displayErrorIfAllLocksCouldNotBeAcquired({
          error: new AggregateError(results.filter((r) => r.status === "rejected").map((r) => r.reason)),
          title: t("operations.wizard.originsLocked"),
          message: t("contextMenu.edit.someoneEditingThem"),
          beingEditedBy: (name) => t("contextMenu.edit.beingEditedBy", { name }),
          addAlert: getRootStore().uiStore.addAlert,
        });
        if (!shown) console.error("Could not lock the operation origins", failure.reason);
        return;
      }
      setOpen(true);
    };

    const onCloseHandler = () => {
      void releaseAll(origins);
      setOpen(false);
      closeMenu();
    };

    return (
      <Observer>
        {() => (
          <ContextMenuAction
            onClick={() => void openWithOriginsLocked()}
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
