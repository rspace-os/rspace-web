//@flow
/* eslint-disable react/prop-types */

import React, { type ComponentType, forwardRef, useContext } from "react";
import ContextMenuAction, {
  type ContextMenuRenderOptions,
} from "./ContextMenuAction";
import DeleteOutlineOutlinedIcon from "@mui/icons-material/DeleteOutlineOutlined";
import { Observer } from "mobx-react-lite";
import { match } from "../../../util/Util";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import SearchContext from "../../../stores/contexts/Search";

type DeleteActionArgs = {|
  as: ContextMenuRenderOptions,
  closeMenu: () => void,
  disabled: string,
  selectedResults: Array<InventoryRecord>,
|};

const DeleteAction: ComponentType<DeleteActionArgs> = forwardRef(
  ({ as, closeMenu, disabled, selectedResults }: DeleteActionArgs, ref) => {
    const { search } = useContext(SearchContext);

    const disabledHelp = match<void, string>([
      [() => disabled !== "", disabled],
      [
        () => selectedResults.some((r) => !r.canEdit),
        `You do not have permission to delete ${
          selectedResults.length > 1 ? "these items" : "this item"
        }.`,
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
            label="Trash"
            disabledHelp={disabledHelp}
            as={as}
            ref={ref}
          />
        )}
      </Observer>
    );
  }
);

DeleteAction.displayName = "DeleteAction";
export default DeleteAction;
