import { Observer } from "mobx-react-lite";
import type React from "react";
import { forwardRef } from "react";
import { useTranslation } from "react-i18next";
import DoubleEditIcon from "../../../assets/graphics/DoubleEditIcon";
import SingleEditIcon from "../../../assets/graphics/SingleEditIcon";
import { mkAlert } from "../../../stores/contexts/Alert";
import type { InventoryRecord, LockStatus } from "../../../stores/definitions/InventoryRecord";
import { RecordLockedError } from "../../../stores/models/InventoryBaseRecord";
import useStores from "../../../stores/use-stores";
import { getErrorMessage, UserCancelledAction } from "../../../util/error";
import RsSet from "../../../util/set";
import type { AllSettled } from "../../../util/types";
import { match, partitionAllSettled } from "../../../util/Util";
import ContextMenuAction, { type ContextMenuRenderOptions } from "./ContextMenuAction";

type EditActionArgs = {
  as: ContextMenuRenderOptions;
  disabled: string;
  selectedResults: Array<InventoryRecord>;
  closeMenu: () => void;
};

const EditAction = forwardRef<React.ElementRef<typeof ContextMenuAction>, EditActionArgs>(
  ({ as, disabled, selectedResults, closeMenu }: EditActionArgs, ref) => {
    const { t } = useTranslation(["inventory", "common"]);
    const { searchStore, uiStore } = useStores();

    const displayErrorIfAllLocksCouldNotBeAcquired = (error: Error): boolean => {
      const isBatchEdit = selectedResults.length > 1;
      const lockedRecords = [];
      if (error instanceof AggregateError) {
        for (const err of error.errors) {
          if (err instanceof RecordLockedError) {
            lockedRecords.push({
              record: err.record,
              lockOwner: err.lockOwner,
            });
          }
        }
      }
      if (error instanceof RecordLockedError) {
        lockedRecords.push({
          record: error.record,
          lockOwner: error.lockOwner,
        });
      }
      if (lockedRecords.length > 0) {
        uiStore.addAlert(
          mkAlert({
            title: isBatchEdit ? t("contextMenu.edit.cannotEditSome") : t("contextMenu.edit.cannotEditThis"),
            message: isBatchEdit ? t("contextMenu.edit.someoneEditingThem") : t("contextMenu.edit.someoneEditingIt"),
            variant: "error",
            isInfinite: true,
            details: lockedRecords.map(({ record, lockOwner }) => ({
              title: record.name,
              record,
              variant: "error",
              help: t("contextMenu.edit.beingEditedBy", {
                firstName: lockOwner.firstName,
                lastName: lockOwner.lastName,
                username: lockOwner.username,
              }),
            })),
          }),
        );
        return true;
      }
      return false;
    };

    const removeAllExistingLocks = async () => {
      await searchStore.search.setActiveResult(null, {
        defaultToFirstResult: false,
      });
    };

    const acquireLocksForSelectedRecords = async () => {
      const editStatuses: AllSettled<LockStatus> = await Promise.allSettled(
        selectedResults.map((r) => r.setEditing(true, true, true)),
      );
      return partitionAllSettled(editStatuses);
    };

    const showWarningIfAnyWereAlreadyLocked = (responses: Array<LockStatus>) => {
      if (responses.includes("WAS_ALREADY_LOCKED")) {
        uiStore.addAlert(
          mkAlert({
            title: t("contextMenu.edit.unsavedChanges.title"),
            message: t("contextMenu.edit.unsavedChanges.message"),
            variant: "warning",
            isInfinite: true,
          }),
        );
      }
    };

    const releaseAllLocks = async () => {
      await Promise.all(selectedResults.map((r) => r.setEditing(false, true, true)));
    };

    /*
     * Would be good if this could live in SearchStore, but importing Alert in
     * SearchStore introduces a cyclical dependency.
     */
    const doEdit = async () => {
      const isBatchEdit = selectedResults.length > 1;

      try {
        if (isBatchEdit) {
          if (!(await uiStore.confirmDiscardAnyChanges())) throw new Error(t("contextMenu.edit.unsavedChanges.error"));
          searchStore.search.setEditLoading("batch");
          await removeAllExistingLocks();
          const { fulfilled, rejected } = await acquireLocksForSelectedRecords();

          if (rejected.length > 0) throw new AggregateError(rejected);
          showWarningIfAnyWereAlreadyLocked(fulfilled);

          await searchStore.search.enableBatchEditing(new RsSet(selectedResults));
        } else {
          searchStore.search.setEditLoading("single");
          // setActiveResult also clears all edit locks
          await searchStore.search.setActiveResult(selectedResults[0]);
          await searchStore.activeResult?.setEditing(true, null, true);
        }
        uiStore.setVisiblePanel("right");
      } catch (e) {
        console.error(e);
        if (!(e instanceof Error)) return;
        if (e instanceof UserCancelledAction) return;
        try {
          if (isBatchEdit) await releaseAllLocks(); // prevent partial acquisition
        } catch {
          // don't need to display this error
        }
        const displayedError = displayErrorIfAllLocksCouldNotBeAcquired(e);
        if (!displayedError) {
          uiStore.addAlert(
            mkAlert({
              title: t("contextMenu.edit.loadFailed"),
              message: !(e instanceof AggregateError)
                ? getErrorMessage(e, t("errors.unknownReason"))
                : t("errors.expandForMoreDetails"),
              variant: "error",
              details:
                e instanceof AggregateError
                  ? e.errors.map((error: unknown) => ({
                      variant: "error",
                      title: getErrorMessage(error, t("errors.unknownReason")),
                    }))
                  : [],
            }),
          );
        }
      } finally {
        searchStore.search.setEditLoading("no");
        closeMenu();
      }
    };

    const disabledHelp = match<void, string>([
      [() => disabled !== "", disabled],
      [
        () => selectedResults.length > 1 && selectedResults.some((r) => !r.supportsBatchEditing),
        t("contextMenu.edit.batchUnsupported", {
          globalIds: selectedResults
            .filter((r) => !r.supportsBatchEditing)
            .map((r) => r.globalId)
            .join(", "),
        }),
      ],
      [() => selectedResults.length === 0, t("contextMenu.edit.nothingSelected")],
      [() => !selectedResults.every((r) => r.canEdit), t("contextMenu.edit.noPermission")],
      [() => searchStore.search.editLoading !== "no", t("common:loading")],
      [() => true, ""],
    ]);

    return (
      <Observer>
        {() => (
          <ContextMenuAction
            onClick={() => void doEdit()}
            icon={selectedResults.length > 1 ? <DoubleEditIcon /> : <SingleEditIcon />}
            label={selectedResults.length > 1 ? t("contextMenu.edit.batchEdit") : t("common:actions.edit")}
            disabledHelp={disabledHelp()}
            as={as}
            ref={ref}
          />
        )}
      </Observer>
    );
  },
);

EditAction.displayName = "EditAction";
export default EditAction;
