import React, { forwardRef } from "react";
import ContextMenuAction, {
  type ContextMenuRenderOptions,
} from "./ContextMenuAction";
import { StyledMenuItem } from "../../../components/StyledMenu";
import { Observer } from "mobx-react-lite";
import AddBoxIcon from "@mui/icons-material/AddBox";
import CreateDialog from "./CreateDialog";
import { match } from "../../../util/Util";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import ContainerModel from "../../../stores/models/ContainerModel";

type CreateActionArgs = {
  as: ContextMenuRenderOptions;
  disabled: string;
  selectedResults: Array<InventoryRecord>;
  closeMenu: () => void;
};

const CreateAction = forwardRef<
  React.ElementRef<typeof StyledMenuItem>,
  CreateActionArgs
>(({ as, disabled, selectedResults, closeMenu }, ref) => {
  const [openCreateDialog, setOpenCreateDialog] = React.useState(false);

  const isFullContainer = (): boolean =>
    Boolean(
      selectedResults[0] instanceof ContainerModel && selectedResults[0].isFull
    );

  const onClick = () => {
    setOpenCreateDialog(true);
  };

  const onCloseHandler = () => {
    setOpenCreateDialog(false);
    closeMenu();
  };

  const disabledHelp = match<void, string>([
    [() => disabled !== "", disabled],
    [() => selectedResults.length === 0, "Nothing is selected."],
    [() => selectedResults.length > 1, "Can only create from a single item."],
    [
      () => !selectedResults[0].permittedActions.has("READ"),
      "You do not have permission to create new items from this item.",
    ],
    [
      () => isFullContainer(),
      "This Container has no free available locations.",
    ],
    [() => true, ""],
  ])();

  return (
    <Observer>
      {() => (
        <ContextMenuAction
          onClick={onClick}
          icon={<AddBoxIcon />}
          label="Create"
          disabledHelp={disabledHelp}
          as={as}
          ref={ref}
        >
          <CreateDialog
            // the key causes the local state to be cleared when closing
            key={openCreateDialog ? 1 : 0}
            open={openCreateDialog}
            onClose={onCloseHandler}
            existingRecord={selectedResults[0]}
          />
        </ContextMenuAction>
      )}
    </Observer>
  );
});

CreateAction.displayName = "CreateAction";
export default CreateAction;
