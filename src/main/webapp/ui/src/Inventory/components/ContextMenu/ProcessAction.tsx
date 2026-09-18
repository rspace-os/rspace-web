import { faCodeBranch } from "@fortawesome/free-solid-svg-icons/faCodeBranch";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import type MenuItem from "@mui/material/MenuItem";
import { Observer } from "mobx-react-lite";
import React, { forwardRef } from "react";
import { useTranslation } from "react-i18next";
import { mkAlert } from "@/stores/contexts/Alert";
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

const releaseAll = (records: Array<SubSampleModel>) => Promise.allSettled(records.map((r) => r.releaseLock(true)));

const ProcessAction = forwardRef<React.ElementRef<typeof MenuItem>, ProcessActionArgs>(
  ({ as, disabled, selectedResults, closeMenu }, ref) => {
    const { t } = useTranslation("inventory");
    const [open, setOpen] = React.useState(false);
    const origins = selectedResults.filter((r): r is SubSampleModel => r instanceof SubSampleModel);

    // The origins this wizard actually locked, so close gives back only those. A fulfilled
    // acquisition may be WAS_ALREADY_LOCKED, meaning another tab or an edit form already held it;
    // releasing that would strip protection the wizard never took.
    const taken = React.useRef<Array<SubSampleModel>>([]);

    // The origins the open wizard is working on, fixed at the moment they were locked. The toolbar
    // can swap the selection while the POSTs are in flight, and the wizard must stay on the records
    // whose locks this action actually holds.
    const [lockedOrigins, setLockedOrigins] = React.useState<Array<SubSampleModel>>([]);

    // One acquisition lifecycle at a time. The entry stays clickable while acquisition is pending,
    // so without this a double click starts a second round whose WAS_ALREADY_LOCKED results (the
    // first round already holds the locks) would overwrite taken.current and leak them at close.
    // A ref, not state, because both clicks can land before React re-renders.
    const busy = React.useRef(false);

    // The toolbar owns the selection, so emptying it unmounts this action mid-acquisition. Nothing
    // is left to close the wizard then, so a lock granted after that point has to be given back
    // here or it stays held until the server expires it.
    const unmounted = React.useRef(false);
    React.useEffect(
      () => () => {
        unmounted.current = true;
        void releaseAll(taken.current);
        taken.current = [];
      },
      [],
    );

    const openWithOriginsLocked = async () => {
      if (busy.current) return;
      busy.current = true;
      let opened = false;
      try {
        const attempted = origins;
        const results = await Promise.allSettled(attempted.map((o) => o.acquireEditLock()));
        const newlyLocked = attempted.filter((_, i) => {
          const result = results[i];
          return result.status === "fulfilled" && result.value === "LOCKED_OK";
        });
        // Unmounted while these were in flight: there is no wizard left to close, so give back
        // whatever was granted rather than recording it against a component that is gone.
        if (unmounted.current) {
          await releaseAll(newlyLocked);
          return;
        }
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
        // WAS_ALREADY_LOCKED names the holder by username alone, so the holder may be this user's
        // own edit form in another tab. That tab still holds the quantity the user typed there and
        // will send it on save, overwriting whatever this operation commits. Refusing to start is
        // the only guard: the lock cannot tell the two sessions apart.
        if (newlyLocked.length < origins.length) {
          await releaseAll(newlyLocked);
          getRootStore().uiStore.addAlert(
            mkAlert({
              title: t("operations.wizard.originsLocked"),
              message: t("operations.wizard.originsOpenElsewhere"),
              variant: "error",
            }),
          );
          return;
        }
        taken.current = newlyLocked;
        setLockedOrigins(newlyLocked);
        opened = true;
        setOpen(true);
      } finally {
        // Held past a successful open: the wizard is the lifecycle, and onCloseHandler ends it.
        if (!opened) busy.current = false;
      }
    };

    const onCloseHandler = () => {
      // The guard stays up until the release settles. Reopening before then would acquire the lock
      // this close is still giving back, and the late DELETE would strip the reopened wizard's own.
      const releasing = releaseAll(taken.current);
      taken.current = [];
      setOpen(false);
      closeMenu();
      void releasing.finally(() => {
        busy.current = false;
      });
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
              <OperationWizard
                key={open ? 1 : 0}
                open={open}
                onClose={onCloseHandler}
                origins={open ? lockedOrigins : origins}
              />
            ) : null}
          </ContextMenuAction>
        )}
      </Observer>
    );
  },
);

ProcessAction.displayName = "ProcessAction";
export default ProcessAction;
