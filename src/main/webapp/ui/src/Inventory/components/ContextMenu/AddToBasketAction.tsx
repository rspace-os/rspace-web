import PostAddIcon from "@mui/icons-material/PostAdd";
import { Observer } from "mobx-react-lite";
import type React from "react";
import { forwardRef, useState } from "react";
import { useTranslation } from "react-i18next";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import { match } from "../../../util/Util";
import AddToBasketDialog from "../Baskets/AddToBasketDialog";
import ContextMenuAction, { type ContextMenuRenderOptions } from "./ContextMenuAction";

type AddToBasketActionArgs = {
  as: ContextMenuRenderOptions;
  closeMenu: () => void;
  disabled: string;
  selectedResults: Array<InventoryRecord>;
};

const AddToBasketAction = forwardRef<React.ElementRef<typeof ContextMenuAction>, AddToBasketActionArgs>(
  ({ as, closeMenu, disabled, selectedResults }: AddToBasketActionArgs, ref) => {
    const { t } = useTranslation("inventory");
    const [openAddToBasketDialog, setOpenAddToBasketDialog] = useState(false);

    const handleOpen = () => {
      setOpenAddToBasketDialog(true);
    };

    const disabledHelp = match<void, string>([
      [() => disabled !== "", disabled],
      [() => selectedResults.some((r) => r.type === "SAMPLE_TEMPLATE"), `Templates cannot be added to a Basket.`],
      [() => true, ""],
    ])();

    return (
      <Observer>
        {() => (
          <ContextMenuAction
            onClick={handleOpen}
            icon={<PostAddIcon />}
            label={t("contextMenu.actions.addToBasket")}
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
  },
);

AddToBasketAction.displayName = "AddToBasketAction";
export default AddToBasketAction;
