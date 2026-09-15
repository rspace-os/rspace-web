import { Observer } from "mobx-react-lite";
import type React from "react";
import { forwardRef } from "react";
import { useTranslation } from "react-i18next";
import { formatList } from "@/modules/common/i18n/listFormat";
import DoubleEditIcon from "../../../assets/graphics/DoubleEditIcon";
import SingleEditIcon from "../../../assets/graphics/SingleEditIcon";
import { mkAlert } from "../../../stores/contexts/Alert";
import type { InventoryRecord, LockStatus } from "../../../stores/definitions/InventoryRecord";
import useStores from "../../../stores/use-stores";
import { getErrorMessage, UserCancelledAction } from "../../../util/error";
import RsSet from "../../../util/set";
import type { AllSettled } from "../../../util/types";
import { match, partitionAllSettled } from "../../../util/Util";
import ContextMenuAction, { type ContextMenuRenderOptions } from "./ContextMenuAction";
import { displayErrorIfAllLocksCouldNotBeAcquired } from "./lockAlerts";

type EditActionArgs = {
  as: ContextMenuRenderOptions;
  disabled: string;
  selectedResults: Array<InventoryRecord>;
  closeMenu: () => void;
};

const EditAction = forwardRef<React.ElementRef<typeof ContextMenuAction>, EditActionArgs>(
  ({ as, disabled, selectedResults, closeMenu }: EditActionArgs, ref) => {
    const { t, i18n } = useTranslation(["inventory", "common"]);
    const language = i18n.resolvedLanguage ?? i18n.language;
    const { searchStore, uiStore } = useStores();
    const isBatchSelection = selectedResults.length > 1;

    const showLockedAlert = (error: Error): boolean =>
      displayErrorIfAllLocksCouldNotBeAcquired({
        error,
        title: isBatchSelection ? t("contextMenu.edit.cannotEditSome") : t("contextMenu.edit.cannotEditThis"),
        message: isBatchSelection ? t("contextMenu.edit.someoneEditingThem") : t("contextMenu.edit.someoneEditingIt"),
        beingEditedBy: (name) => t("contextMenu.edit.beingEditedBy", { name }),
        addAlert: uiStore.addAlert.bind(uiStore),
      });

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
        const displayedError = showLockedAlert(e);
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
          globalIds: formatList(
            selectedResults.filter((r) => !r.supportsBatchEditing).flatMap((r) => (r.globalId ? [r.globalId] : [])),
            language,
          ),
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
