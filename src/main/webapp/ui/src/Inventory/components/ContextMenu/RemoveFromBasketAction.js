//@flow

import React, { type ComponentType, forwardRef, useContext } from "react";
import ContextMenuAction, {
  type ContextMenuRenderOptions,
} from "./ContextMenuAction";
import RemoveFromBasketIcon from "./RemoveFromBasketIcon";
import { Observer } from "mobx-react-lite";
import { match } from "../../../util/Util";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import useStores from "../../../stores/use-stores";
import NavigateContext, {
  type UseLocation,
} from "../../../stores/contexts/Navigate";
import {
  type GlobalId,
  getSavedGlobalId,
} from "../../../stores/definitions/BaseRecord";

type RemoveFromBasketActionArgs = {|
  as: ContextMenuRenderOptions,
  closeMenu: () => void,
  disabled: string,
  selectedResults: Array<InventoryRecord>,
|};

const RemoveFromBasketAction: ComponentType<RemoveFromBasketActionArgs> =
  forwardRef(
    (
      { as, closeMenu, disabled, selectedResults }: RemoveFromBasketActionArgs,
      ref
    ) => {
      const { searchStore } = useStores();
      const { useLocation } = useContext(NavigateContext);
      function useSearchParams() {
        return new URLSearchParams((useLocation(): UseLocation).search);
      }
      const searchParams = useSearchParams();

      const itemIds: Array<GlobalId> = selectedResults.map((r) =>
        getSavedGlobalId(r)
      );

      const onRemove = () => {
        const currentBasket = searchStore.savedBaskets.find(
          (b) => b.globalId === searchParams.get("parentGlobalId")
        );
        if (currentBasket) void currentBasket.removeItems(itemIds);
      };

      const handleOpen = () => {
        onRemove();
        if (typeof closeMenu === "function") closeMenu();
      };

      const disabledHelp = match<void, string>([
        [() => disabled !== "", disabled],
        [() => true, ""],
      ])();

      return (
        <Observer>
          {() => (
            <>
              <ContextMenuAction
                onClick={handleOpen}
                icon={<RemoveFromBasketIcon />}
                label="Remove from This Basket"
                disabledHelp={disabledHelp}
                as={as}
                ref={ref}
              />
            </>
          )}
        </Observer>
      );
    }
  );

RemoveFromBasketAction.displayName = "RemoveFromBasketAction";
export default RemoveFromBasketAction;
