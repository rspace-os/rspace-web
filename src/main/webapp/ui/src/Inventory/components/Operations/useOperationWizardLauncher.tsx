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
  onClose?: (performed: boolean) => void;
};

/** The caller must render `wizard`. */
export function useOperationWizardLauncher(
  origins: Array<SubSampleModel>,
  { onPerformed, onClose }: LauncherOptions = {},
): { launch: () => Promise<boolean>; wizard: React.ReactNode } {
  const { t } = useTranslation("inventory");
  const available = useProcessAvailable();
  const [open, setOpen] = React.useState(false);

  // A fulfilled acquisition may be WAS_ALREADY_LOCKED (another tab or an edit form holds it);
  // releasing that would strip protection the wizard never took.
  const taken = React.useRef<Array<SubSampleModel>>([]);

  const [lockedOrigins, setLockedOrigins] = React.useState<Array<SubSampleModel>>([]);

  // A ref, not state: both clicks of a double click can land before React re-renders.
  const busy = React.useRef(false);

  const performed = React.useRef(false);

  // A renewal is a POST that recreates the lock, so a release must wait for any in flight.
  const renewals = React.useRef<Promise<unknown>>(Promise.resolve());

  const unmounted = React.useRef(false);
  // The cleanup can run without an unmount (hot reload, StrictMode, a hidden Activity), so setup
  // undoes it and `taken` is kept: the open wizard's next renewal re-takes the locks, and close
  // must still give them back.
  React.useEffect(() => {
    unmounted.current = false;
    return () => {
      unmounted.current = true;
      const held = taken.current;
      void renewals.current.then(() => releaseAll(held));
    };
  }, []);

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
      if (!opened) busy.current = false;
    }
  };

  const close = () => {
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
