import React, { type ReactElement } from "react";
import { menuIDs } from "../../../util/menuIDs";
import { type SplitButtonOption } from "../../components/ContextMenu/ContextMenuSplitButton";
import { type ContextMenuRenderOptions } from "./ContextMenuAction";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import SelectAction from "./SelectAction";
import EditAction from "./EditAction";
import DeleteAction from "./DeleteAction";
import DuplicateAction from "./DuplicateAction";
import MoveAction from "./MoveAction";
import RestoreAction from "./RestoreAction";
import TransferAction from "./TransferAction";
import CreateAction from "./CreateAction";
import AddToBasketAction from "./AddToBasketAction";
import RemoveFromBasketAction from "./RemoveFromBasketAction";
import ExportAction from "./ExportAction";
import PrintBarcodeAction from "./PrintBarcodeAction";

type ContextActionsArgs = {
  selectedResults: Array<InventoryRecord>;
  mixedSelectedStatus?: boolean;
  forceDisabled?: string;
  closeMenu: () => void;
  onSelectOptions?: Array<SplitButtonOption>;
  menuID: (typeof menuIDs)[keyof typeof menuIDs];
  basketSearch: boolean;
};

type ContextAction = {
  component: ReactElement;
  hidden: boolean;
};

const contextActions = ({
  selectedResults,
  mixedSelectedStatus = false,
  forceDisabled = "",
  onSelectOptions,
  closeMenu,
  menuID,
  basketSearch,
}: ContextActionsArgs): ((
  as: ContextMenuRenderOptions
) => Array<ContextAction>) => {
  const contextActionsGenerator = (as: ContextMenuRenderOptions) => {
    const allSelectedAvailable: boolean = selectedResults.every(
      (r: InventoryRecord) => !r.deleted
    );
    const allSelectedDeleted: boolean = selectedResults.every(
      (r: InventoryRecord) => r.deleted
    );

    const reasonsToDisableContextMenu = selectedResults
      .map((r: InventoryRecord) => r.contextMenuDisabled())
      .filter(Boolean);
    const disableAllActions: string =
      forceDisabled ||
      (reasonsToDisableContextMenu.length > 0
        ? reasonsToDisableContextMenu[0] || ""
        : "");

    const hideInPickerAndWhenNotAllCurrent =
      menuID === menuIDs.PICKER || allSelectedDeleted || mixedSelectedStatus;

    return [
      {
        component: (
          <SelectAction
            key="select"
            selectedResults={selectedResults}
            onSelectOptions={onSelectOptions}
            as={as}
            disabled={disableAllActions}
          />
        ),
        hidden: new Set([menuIDs.STEPPER, menuIDs.CONTENT, menuIDs.CARD]).has(
          menuID
        ),
      },
      {
        component: (
          <EditAction
            key="edit"
            selectedResults={selectedResults}
            as={as}
            disabled={disableAllActions}
            closeMenu={closeMenu}
          />
        ),
        hidden: hideInPickerAndWhenNotAllCurrent,
      },
      {
        component: (
          <CreateAction
            key="create"
            selectedResults={selectedResults}
            as={as}
            disabled={disableAllActions}
            closeMenu={closeMenu}
          />
        ),
        hidden: hideInPickerAndWhenNotAllCurrent,
      },
      {
        component: (
          <DuplicateAction
            key="duplicate"
            selectedResults={selectedResults}
            as={as}
            disabled={disableAllActions}
            closeMenu={closeMenu}
          />
        ),
        hidden: allSelectedDeleted || mixedSelectedStatus,
      },
      {
        component: (
          <MoveAction
            key="move"
            selectedResults={selectedResults}
            as={as}
            disabled={disableAllActions}
            closeMenu={closeMenu}
          />
        ),
        hidden: hideInPickerAndWhenNotAllCurrent,
      },
      {
        component: (
          <TransferAction
            key="transfer"
            selectedResults={selectedResults}
            as={as}
            disabled={disableAllActions}
            closeMenu={closeMenu}
          />
        ),
        hidden: hideInPickerAndWhenNotAllCurrent,
      },
      {
        component: (
          <RestoreAction
            key="restore"
            selectedResults={selectedResults}
            as={as}
            disabled={disableAllActions}
            closeMenu={closeMenu}
          />
        ),
        hidden:
          menuID === menuIDs.PICKER ||
          allSelectedAvailable ||
          mixedSelectedStatus,
      },
      {
        component: (
          <AddToBasketAction
            key="add to basket"
            selectedResults={selectedResults}
            as={as}
            disabled={disableAllActions}
            closeMenu={closeMenu}
          />
        ),
        hidden: menuID === menuIDs.PICKER,
      },
      {
        component: (
          <RemoveFromBasketAction
            key="remove from basket"
            selectedResults={selectedResults}
            as={as}
            disabled={disableAllActions}
            closeMenu={closeMenu}
          />
        ),
        hidden: menuID === menuIDs.PICKER || !basketSearch,
      },
      {
        component: (
          <ExportAction
            key="export"
            selectedResults={selectedResults}
            as={as}
            disabled={disableAllActions}
            closeMenu={closeMenu}
          />
        ),
        hidden: hideInPickerAndWhenNotAllCurrent,
      },
      {
        component: (
          <PrintBarcodeAction
            key="print barcode"
            selectedResults={selectedResults}
            as={as}
            disabled={disableAllActions}
            closeMenu={closeMenu}
          />
        ),
        hidden: menuID === menuIDs.PICKER,
      },
      {
        component: (
          <DeleteAction
            key="delete"
            selectedResults={selectedResults}
            as={as}
            disabled={disableAllActions}
            closeMenu={closeMenu}
          />
        ),
        hidden: hideInPickerAndWhenNotAllCurrent,
      },
    ];
  };
  return contextActionsGenerator;
};

export default contextActions;
