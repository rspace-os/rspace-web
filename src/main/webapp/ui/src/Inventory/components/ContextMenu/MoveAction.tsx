import React, { forwardRef } from "react";
import ContextMenuAction, {
  type ContextMenuRenderOptions,
} from "./ContextMenuAction";
import OpenWithIcon from "@mui/icons-material/OpenWith";
import useStores from "../../../stores/use-stores";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import { match } from "../../../util/Util";
import { Observer } from "mobx-react-lite";

type MoveActionArgs = {
  as: ContextMenuRenderOptions;
  selectedResults: Array<InventoryRecord>;
  disabled: string;
  closeMenu: () => void;
};

const MoveAction = forwardRef<
  React.ElementRef<typeof ContextMenuAction>,
  MoveActionArgs
>(({ as, selectedResults, disabled, closeMenu }, ref) => {
  const { moveStore } = useStores();

  const handleOpen = () => {
    void moveStore.setIsMoving(true);
    moveStore.setSelectedResults(selectedResults);
    closeMenu();
  };

  const invalidTypeSelected = () =>
    selectedResults.some((r) => r.recordType === "sampleTemplate");

  const disabledHelp = match<void, string>([
    [() => disabled !== "", disabled],
    [() => invalidTypeSelected(), "Templates cannot be moved."],
    [
      () => selectedResults.some((r) => !r.canEdit),
      `You do not have permission to move ${
        selectedResults.length > 1 ? "these items" : "this item"
      }.`,
    ],
    [() => true, ""],
  ])();
  return (
    <Observer>
      {() => (
        <ContextMenuAction
          onClick={handleOpen}
          icon={<OpenWithIcon />}
          label="Move"
          as={as}
          ref={ref}
          disabledHelp={disabledHelp}
        />
      )}
    </Observer>
  );
});

MoveAction.displayName = "MoveAction";
export default MoveAction;
