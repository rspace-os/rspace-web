import React from "react";
import { useTranslation } from "react-i18next";
import { mkAlert } from "@/stores/contexts/Alert";
import type { InventoryRecord } from "@/stores/definitions/InventoryRecord";
import SubSampleModel from "@/stores/models/SubSampleModel";
import getRootStore from "@/stores/stores/getRootStore";
import { displayErrorIfAllLocksCouldNotBeAcquired } from "../ContextMenu/lockAlerts";
import { useProcessAvailable } from "../ContextMenu/useProcessAvailable";
import OperationWizard from "./OperationWizard";
import type { OperationResult } from "./operationsApi";

export function isProcessableSelection(records: ReadonlyArray<InventoryRecord>): boolean {
  return records.length >= 1 && records.every((r) => r instanceof SubSampleModel);
}

const releaseAll = (records: Array<SubSampleModel>) => Promise.allSettled(records.map((r) => r.releaseLock(true)));

type LauncherOptions = {
  onPerformed?: (sample: OperationResult | null) => void;
  /** Called after the locks are released; `performed` is false when the user cancelled. */
  onClose?: (performed: boolean) => void;
};

/**
 * The only sanctioned way to open the operation wizard: it runs the same availability checks the
 * context menu runs, and owns the origins' edit locks for the wizard's whole lifetime. `launch`
 * resolves to whether the wizard opened; `wizard` must be rendered by the caller.
 */
export function useOperationWizardLauncher(
  origins: Array<SubSampleModel>,
  { onPerformed, onClose }: LauncherOptions = {},
): { launch: () => Promise<boolean>; wizard: React.ReactNode } {
  const { t } = useTranslation("inventory");
  const available = useProcessAvailable();
  const [open, setOpen] = React.useState(false);

  // The origins this wizard actually locked, so close gives back only those. A fulfilled
  // acquisition may be WAS_ALREADY_LOCKED, meaning another tab or an edit form already held it;
  // releasing that would strip protection the wizard never took.
  const taken = React.useRef<Array<SubSampleModel>>([]);

  // The origins the open wizard is working on, fixed at the moment they were locked. The toolbar
  // can swap the selection while the POSTs are in flight, and the wizard must stay on the records
  // whose locks this launcher actually holds.
  const [lockedOrigins, setLockedOrigins] = React.useState<Array<SubSampleModel>>([]);

  // One acquisition lifecycle at a time. A double click would otherwise start a second round whose
  // WAS_ALREADY_LOCKED results (the first round already holds the locks) would overwrite
  // taken.current and leak them at close. A ref, not state, because both clicks can land before
  // React re-renders.
  const busy = React.useRef(false);

  const performed = React.useRef(false);

  // The open wizard keeps its outstanding lock renewals here. A renewal is a POST that recreates
  // the lock, so releasing while one is in flight lets the DELETE go first and the late POST
  // re-locks an origin nothing is left to release.
  const renewals = React.useRef<Promise<unknown>>(Promise.resolve());

  // The toolbar owns the selection, so emptying it unmounts the caller mid-acquisition. Nothing
  // is left to close the wizard then, so a lock granted after that point has to be given back
  // here or it stays held until the server expires it.
  const unmounted = React.useRef(false);
  React.useEffect(
    () => () => {
      unmounted.current = true;
      const held = taken.current;
      taken.current = [];
      void renewals.current.then(() => releaseAll(held));
    },
    [],
  );

  const launch = async (): Promise<boolean> => {
    if (!available) {
      getRootStore().uiStore.addAlert(
        mkAlert({
          title: t("operations.wizard.originsLocked"),
          message: t("operations.wizard.notEnabled"),
          variant: "notice",
        }),
      );
      return false;
    }
    if (!isProcessableSelection(origins) || origins.some((o) => o.deleted)) return false;
    if (busy.current) return false;
    busy.current = true;
    let opened = false;
    try {
      const attempted = origins;
      const results = await Promise.allSettled(attempted.map((o) => o.acquireEditLock()));
      const newlyLocked = attempted.filter((_, i) => {
        const result = results[i];
        return result.status === "fulfilled" && result.value === "LOCKED_OK";
      });
      if (unmounted.current) {
        await releaseAll(newlyLocked);
        return false;
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
        return false;
      }
      // WAS_ALREADY_LOCKED names the holder by username alone, so the holder may be this user's
      // own edit form in another tab. That tab still holds the quantity the user typed there and
      // will send it on save, overwriting whatever this operation commits.
      if (newlyLocked.length < origins.length) {
        await releaseAll(newlyLocked);
        getRootStore().uiStore.addAlert(
          mkAlert({
            title: t("operations.wizard.originsLocked"),
            message: t("operations.wizard.originsOpenElsewhere"),
            variant: "error",
          }),
        );
        return false;
      }
      taken.current = newlyLocked;
      performed.current = false;
      setLockedOrigins(newlyLocked);
      opened = true;
      setOpen(true);
      return true;
    } finally {
      // Held past a successful open: the wizard is the lifecycle, and close ends it.
      if (!opened) busy.current = false;
    }
  };

  const close = () => {
    // The guard stays up until the release settles. Reopening before then would acquire the lock
    // this close is still giving back, and the late DELETE would strip the reopened wizard's own.
    const releasing = releaseAll(taken.current);
    taken.current = [];
    setOpen(false);
    void releasing.finally(() => {
      busy.current = false;
      onClose?.(performed.current);
    });
  };

  const wizard =
    open || origins.length > 0 ? (
      <OperationWizard
        key={open ? 1 : 0}
        open={open}
        onClose={close}
        onPerformed={(sample) => {
          performed.current = true;
          onPerformed?.(sample);
        }}
        origins={open ? lockedOrigins : origins}
        pendingRenewals={renewals}
      />
    ) : null;

  return { launch, wizard };
}
