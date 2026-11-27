import OpenWithIcon from "@mui/icons-material/OpenWith";
import { Observer } from "mobx-react-lite";
import type React from "react";
import { forwardRef } from "react";
import { useTranslation } from "react-i18next";
import { mkAlert } from "../../../stores/contexts/Alert";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import useStores from "../../../stores/use-stores";
import { getErrorMessage } from "../../../util/error";
import { match } from "../../../util/Util";
import ContextMenuAction, { type ContextMenuRenderOptions } from "./ContextMenuAction";

type MoveActionArgs = {
  as: ContextMenuRenderOptions;
  selectedResults: Array<InventoryRecord>;
  disabled: string;
  closeMenu: () => void;
};

const MoveAction = forwardRef<React.ElementRef<typeof ContextMenuAction>, MoveActionArgs>(
  ({ as, selectedResults, disabled, closeMenu }, ref) => {
    const { t } = useTranslation(["inventory", "common"]);
    const { moveStore, uiStore } = useStores();

    const handleOpen = () => {
      void moveStore.setIsMoving(true);
      void moveStore.setSelectedResults(selectedResults).catch((error) => {
        console.error("Could not load items to move", error);
        uiStore.addAlert(
          mkAlert({
            title: t("moveToTarget.errors.moveFailed"),
            message: getErrorMessage(error, t("errors.unknownReason")),
            variant: "error",
          }),
        );
        void moveStore.setIsMoving(false);
      });
      closeMenu();
    };

    const invalidTypeSelected = () =>
      selectedResults.some((r) => r.recordType === "sampleTemplate" || r.recordType === "instrumentTemplate");

    const disabledHelp = match<void, string>([
      [() => disabled !== "", disabled],
      [() => invalidTypeSelected(), t("contextMenu.move.templatesCannotBeMoved")],
      [
        () => selectedResults.some((r) => !r.canEdit),
        t("contextMenu.move.noPermission", {
          count: selectedResults.length,
        }),
      ],
      [() => true, ""],
    ])();
    return (
      <Observer>
        {() => (
          <ContextMenuAction
            onClick={handleOpen}
            icon={<OpenWithIcon />}
            label={t("common:actions.move")}
            as={as}
            ref={ref}
            disabledHelp={disabledHelp}
          />
        )}
      </Observer>
    );
  },
);

MoveAction.displayName = "MoveAction";
export default MoveAction;
