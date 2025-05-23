//@flow
/* eslint-disable react/prop-types */

import { mkAlert } from "../../../stores/contexts/Alert";
import { RecordLockedError } from "../../../stores/models/InventoryBaseRecord";
import {
  type InventoryRecord,
  type LockStatus,
} from "../../../stores/definitions/InventoryRecord";
import useStores from "../../../stores/use-stores";
import { match, doNotAwait, partitionAllSettled } from "../../../util/Util";
import RsSet from "../../../util/set";
import ContextMenuAction, {
  type ContextMenuRenderOptions,
} from "./ContextMenuAction";
import { Observer } from "mobx-react-lite";
import React, { type ComponentType, forwardRef } from "react";
import { type AllSettled } from "../../../util/types";
import singleEditIcon from "../../../assets/graphics/SingleEditIcon";
import doubleEditIcon from "../../../assets/graphics/DoubleEditIcon";
import { UserCancelledAction } from "../../../util/error";

type EditActionArgs = {|
  as: ContextMenuRenderOptions,
  disabled: string,
  selectedResults: Array<InventoryRecord>,
  closeMenu: () => void,
|};

const EditAction: ComponentType<EditActionArgs> = forwardRef(
  ({ as, disabled, selectedResults, closeMenu }: EditActionArgs, ref) => {
    const { searchStore, uiStore } = useStores();

    const displayErrorIfAllLocksCouldNotBeAcquired = (
      error: Error
    ): boolean => {
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
            title: isBatchEdit
              ? "Cannot edit some of the selected items."
              : "Cannot edit this item.",
            message: `Someone else is currently editing ${
              isBatchEdit ? "them" : "it"
            }.`,
            variant: "error",
            isInfinite: true,
            details: lockedRecords.map(({ record, lockOwner }) => ({
              title: record.name,
              record,
              variant: "error",
              help: `Being edited by ${lockOwner.firstName} ${lockOwner.lastName} (${lockOwner.username}).`,
            })),
          })
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
        selectedResults.map((r) => r.setEditing(true, true, true))
      );
      return partitionAllSettled(editStatuses);
    };

    const showWarningIfAnyWereAlreadyLocked = (
      responses: Array<LockStatus>
    ) => {
      if (responses.includes("WAS_ALREADY_LOCKED")) {
        uiStore.addAlert(
          mkAlert({
            title: "Unsaved changes?",
            message:
              "It appears that you already started editing some of " +
              "these records in another browser tab or on another " +
              "device. We advise you cancel or save those changes first " +
              "otherwise editing here could result in an error.",
            variant: "warning",
            isInfinite: true,
          })
        );
      }
    };

    const releaseAllLocks = async () => {
      await Promise.all(
        selectedResults.map((r) => r.setEditing(false, true, true))
      );
    };

    /*
     * Would be good if this could live in SearchStore, but importing Alert in
     * SearchStore introduces a cyclical dependency.
     */
    const doEdit = async () => {
      const isBatchEdit = selectedResults.length > 1;

      try {
        if (isBatchEdit) {
          if (!(await uiStore.confirmDiscardAnyChanges()))
            throw new Error("Unsaved changes.");
          searchStore.search.setEditLoading("batch");
          await removeAllExistingLocks();
          const { fulfilled, rejected } =
            await acquireLocksForSelectedRecords();

          if (rejected.length > 0) throw new AggregateError(rejected);
          showWarningIfAnyWereAlreadyLocked(fulfilled);

          await searchStore.search.enableBatchEditing(
            new RsSet(selectedResults)
          );
        } else {
          searchStore.search.setEditLoading("single");
          // setActiveResult also clears all edit locks
          await searchStore.search.setActiveResult(selectedResults[0]);
          await searchStore.activeResult?.setEditing(true, null, true);
        }
        uiStore.setVisiblePanel("right");
      } catch (e) {
        console.error(e);
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
              title: "Could not load edit mode.",
              message: !(e instanceof AggregateError)
                ? e.response?.data.message ?? e.message ?? "Unknown reason."
                : "Expand for more details.",
              variant: "error",
              details:
                e instanceof AggregateError
                  ? // $FlowExpectedError[prop-missing]
                    e.errors.map((error: mixed) => ({
                      variant: "error",
                      title:
                        // $FlowExpectedError[incompatible-use]
                        // $FlowExpectedError[unnecessary-optional-chain]
                        error?.response?.data.message ??
                        // $FlowExpectedError[incompatible-use]
                        error.message ??
                        "Unknown reason.",
                    }))
                  : [],
            })
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
        () =>
          selectedResults.length > 1 &&
          selectedResults.some((r) => !r.supportsBatchEditing),
        `Some of the selected items do not support batch editing: ${selectedResults
          .filter((r) => !r.supportsBatchEditing)
          .map((r) => r.globalId)
          .join(", ")}. Please edit individually.`,
      ],
      [() => selectedResults.length === 0, "Nothing is selected."],
      [
        () => !selectedResults.every((r) => r.canEdit),
        `You do not have permission to edit this item.`,
      ],
      [() => searchStore.search.editLoading !== "no", "Loading"],
      [() => true, ""],
    ]);

    return (
      <Observer>
        {() => (
          <ContextMenuAction
            onClick={doNotAwait(doEdit)}
            icon={selectedResults.length > 1 ? doubleEditIcon : singleEditIcon}
            label={`${selectedResults.length > 1 ? "Batch " : ""}Edit`}
            disabledHelp={disabledHelp()}
            as={as}
            ref={ref}
          />
        )}
      </Observer>
    );
  }
);

EditAction.displayName = "EditAction";
export default EditAction;
