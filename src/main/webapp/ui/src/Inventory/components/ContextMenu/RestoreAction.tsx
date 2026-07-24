import RestoreFromTrashIcon from "@mui/icons-material/RestoreFromTrash";
import { Observer } from "mobx-react-lite";
import type React from "react";
import { forwardRef, useContext } from "react";
import { useTranslation } from "react-i18next";
import SearchContext from "../../../stores/contexts/Search";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import { match } from "../../../util/Util";
import ContextMenuAction, { type ContextMenuRenderOptions } from "./ContextMenuAction";

type RestoreActionArgs = {
  as: ContextMenuRenderOptions;
  disabled: string;
  selectedResults: Array<InventoryRecord>;
  closeMenu: () => void;
};

const RestoreAction = forwardRef<React.ElementRef<typeof ContextMenuAction>, RestoreActionArgs>(
  ({ as, disabled, selectedResults, closeMenu }: RestoreActionArgs, ref) => {
    const { t } = useTranslation(["inventory", "common"]);
    const { search } = useContext(SearchContext);

    const disabledHelp = match<void, string>([
      [() => disabled !== "", disabled],
      [
        () => selectedResults.some((r) => !r.canEdit),
        t("contextMenu.restore.noPermission", {
          count: selectedResults.length,
        }),
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
            label={t("common:actions.restore")}
            disabledHelp={disabledHelp}
            as={as}
            ref={ref}
          />
        )}
      </Observer>
    );
  },
);

RestoreAction.displayName = "RestoreAction";
export default RestoreAction;
