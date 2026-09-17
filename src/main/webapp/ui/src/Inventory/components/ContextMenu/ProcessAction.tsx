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
 * The single home for the gate; ContextMenu visibility (ContextActions) and this
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
 * ContextActions only shows this entry for a processable selection (isProcessableSelection), so no
 * per-action gating remains here.
 */
const ProcessAction = forwardRef<React.ElementRef<typeof MenuItem>, ProcessActionArgs>(
  ({ as, disabled, selectedResults, closeMenu }, ref) => {
    const { t } = useTranslation("inventory");
    const [open, setOpen] = React.useState(false);
    const origins = selectedResults.filter((r): r is SubSampleModel => r instanceof SubSampleModel);

    // The origins this wizard actually locked, so close gives back only those. A fulfilled
    // acquisition may be WAS_ALREADY_LOCKED, meaning another tab or an edit form already held it;
    // releasing that would strip protection the wizard never took.
    const taken = React.useRef<Array<SubSampleModel>>([]);

    // One acquisition lifecycle at a time. The entry stays clickable while acquisition is pending,
    // so without this a double click starts a second round whose WAS_ALREADY_LOCKED results (the
    // first round already holds the locks) would overwrite taken.current and leak them at close.
    // A ref, not state, because both clicks can land before React re-renders.
    const busy = React.useRef(false);

    const releaseAll = (records: Array<SubSampleModel>) => Promise.allSettled(records.map((r) => r.releaseLock(true)));

    // Holds an edit lock on each origin while the wizard is open.
    const openWithOriginsLocked = async () => {
      if (busy.current) return;
      busy.current = true;
      let opened = false;
      try {
        const results = await Promise.allSettled(origins.map((o) => o.acquireEditLock()));
        const newlyLocked = origins.filter((_, i) => {
          const result = results[i];
          return result.status === "fulfilled" && result.value === "LOCKED_OK";
        });
        const failure = results.find((r) => r.status === "rejected");
        if (failure) {
          await releaseAll(newlyLocked);
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
        taken.current = newlyLocked;
        opened = true;
        setOpen(true);
      } finally {
        // Held past a successful open: the wizard is the lifecycle, and onCloseHandler ends it.
        if (!opened) busy.current = false;
      }
    };

    const onCloseHandler = () => {
      void releaseAll(taken.current);
      taken.current = [];
      busy.current = false;
      setOpen(false);
      closeMenu();
    };

    return (
      <Observer>
        {() => (
          <ContextMenuAction
            onClick={() => void openWithOriginsLocked()}
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
