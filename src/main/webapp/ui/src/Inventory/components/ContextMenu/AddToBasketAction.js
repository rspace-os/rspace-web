//@flow

import React, { type ComponentType, forwardRef, useState } from "react";
import ContextMenuAction, {
  type ContextMenuRenderOptions,
} from "./ContextMenuAction";
import PostAddIcon from "@mui/icons-material/PostAdd";
import { Observer } from "mobx-react-lite";
import { match } from "../../../util/Util";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import AddToBasketDialog from "../Baskets/AddToBasketDialog";

type AddToBasketActionArgs = {|
  as: ContextMenuRenderOptions,
  closeMenu: () => void,
  disabled: string,
  selectedResults: Array<InventoryRecord>,
|};

const AddToBasketAction: ComponentType<AddToBasketActionArgs> = forwardRef(
  (
    { as, closeMenu, disabled, selectedResults }: AddToBasketActionArgs,
    ref
  ) => {
    const [openAddToBasketDialog, setOpenAddToBasketDialog] = useState(false);

    const handleOpen = () => {
      setOpenAddToBasketDialog(true);
    };

    const disabledHelp = match<void, string>([
      [() => disabled !== "", disabled],
      [
        () => selectedResults.some((r) => r.type === "SAMPLE_TEMPLATE"),
        `Templates cannot be added to a Basket.`,
      ],
      [() => true, ""],
    ])();

    return (
      <Observer>
        {() => (
          <ContextMenuAction
            onClick={handleOpen}
            icon={<PostAddIcon />}
            label="Add to Basket"
            disabledHelp={disabledHelp}
            as={as}
            ref={ref}
          >
            {openAddToBasketDialog && (
              <AddToBasketDialog
                openAddToBasketDialog={openAddToBasketDialog}
                setOpenAddToBasketDialog={setOpenAddToBasketDialog}
                closeMenu={closeMenu}
                selectedResults={selectedResults}
              />
            )}
          </ContextMenuAction>
        )}
      </Observer>
    );
  }
);

AddToBasketAction.displayName = "AddToBasketAction";
export default AddToBasketAction;
