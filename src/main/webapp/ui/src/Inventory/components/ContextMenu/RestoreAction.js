//@flow
/* eslint-disable react/prop-types */

import React, { type ComponentType, forwardRef, useContext } from "react";
import ContextMenuAction, {
  type ContextMenuRenderOptions,
} from "./ContextMenuAction";
import RestoreFromTrashIcon from "@mui/icons-material/RestoreFromTrash";
import { Observer } from "mobx-react-lite";
import { match } from "../../../util/Util";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import SearchContext from "../../../stores/contexts/Search";

type RestoreActionArgs = {|
  as: ContextMenuRenderOptions,
  disabled: string,
  selectedResults: Array<InventoryRecord>,
  closeMenu: () => void,
|};

const RestoreAction: ComponentType<RestoreActionArgs> = forwardRef(
  ({ as, disabled, selectedResults, closeMenu }: RestoreActionArgs, ref) => {
    const { search } = useContext(SearchContext);

    const disabledHelp = match<void, string>([
      [() => disabled !== "", disabled],
      [
        () => selectedResults.some((r) => !r.canEdit),
        `You do not have permission to restore ${
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
              void search.restoreRecords(selectedResults);
              closeMenu();
            }}
            icon={<RestoreFromTrashIcon />}
            label="Restore"
            disabledHelp={disabledHelp}
            as={as}
            ref={ref}
          />
        )}
      </Observer>
    );
  }
);

RestoreAction.displayName = "RestoreAction";
export default RestoreAction;
