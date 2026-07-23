import OpenWithIcon from "@mui/icons-material/OpenWith";
import { Observer } from "mobx-react-lite";
import type React from "react";
import { forwardRef } from "react";
import { useTranslation } from "react-i18next";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import useStores from "../../../stores/use-stores";
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
    const { moveStore } = useStores();

    const handleOpen = () => {
      void moveStore.setIsMoving(true);
      moveStore.setSelectedResults(selectedResults);
      closeMenu();
    };

    const invalidTypeSelected = () =>
      selectedResults.some((r) => r.recordType === "sampleTemplate" || r.recordType === "instrumentTemplate");

    const disabledHelp = match<void, string>([
      [() => disabled !== "", disabled],
      [() => invalidTypeSelected(), t("contextMenu.move.templatesCannotBeMoved")],
      [
        () => selectedResults.some((r) => !r.canEdit),
        `You do not have permission to move ${selectedResults.length > 1 ? "these items" : "this item"}.`,
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
