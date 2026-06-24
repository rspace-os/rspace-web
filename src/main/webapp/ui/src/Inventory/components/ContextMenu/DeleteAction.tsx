import DeleteOutlineOutlinedIcon from "@mui/icons-material/DeleteOutlineOutlined";
import { Observer } from "mobx-react-lite";
import type React from "react";
import { forwardRef, useContext } from "react";
import { useTranslation } from "react-i18next";
import SearchContext from "../../../stores/contexts/Search";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import { match } from "../../../util/Util";
import ContextMenuAction, { type ContextMenuRenderOptions } from "./ContextMenuAction";

type DeleteActionArgs = {
  as: ContextMenuRenderOptions;
  closeMenu: () => void;
  disabled: string;
  selectedResults: Array<InventoryRecord>;
};

const DeleteAction = forwardRef<React.ElementRef<typeof ContextMenuAction>, DeleteActionArgs>(
  ({ as, closeMenu, disabled, selectedResults }, ref) => {
    const { t } = useTranslation("inventory");
    const { search } = useContext(SearchContext);

    const disabledHelp = match<void, string>([
      [() => disabled !== "", disabled],
      [
        () => selectedResults.some((r) => !r.canEdit),
        `You do not have permission to delete ${selectedResults.length > 1 ? "these items" : "this item"}.`,
      ],
      [() => true, ""],
    ])();

    return (
      <Observer>
        {() => (
          <ContextMenuAction
            onClick={() => {
              void search.deleteRecords(selectedResults);
              closeMenu();
            }}
            icon={<DeleteOutlineOutlinedIcon />}
            label={t("contextMenu.actions.trash")}
            disabledHelp={disabledHelp}
            as={as}
            ref={ref}
          />
        )}
      </Observer>
    );
  },
);

DeleteAction.displayName = "DeleteAction";
export default DeleteAction;
