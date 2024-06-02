//@flow

import React, { type ComponentType, forwardRef } from "react";
import ContextMenuAction, {
  type ContextMenuRenderOptions,
} from "./ContextMenuAction";
import SelectAllIcon from "@mui/icons-material/SelectAll";
import { Observer } from "mobx-react-lite";
import { type SplitButtonOption } from "../../components/ContextMenu/ContextMenuSplitButton";
import Badge from "@mui/material/Badge";
import { match } from "../../../util/Util";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";

type SelectActionArgs = {|
  as: ContextMenuRenderOptions,
  disabled: string,
  onSelectOptions?: Array<SplitButtonOption>,

  /*
   * This has to be a ReadOnlyArray because test code will call this component
   * with an array of some concrete sub-class that if it were to be mutated
   * then the typing would be violated. For example, lets say this component is
   * called with an Array<SubSampleModel>, if `selectedResults` were a regular
   * array then Flow would complain because inside this component we could
   * mutate the array (e.g. push a ContainerModel) and violate the type. As
   * such, we have to make this array read-only to demonstrate to Flow that we
   * don't instend to mutate the array inside this component.
   */
  selectedResults: $ReadOnlyArray<InventoryRecord>,
|};

const SelectAction: ComponentType<SelectActionArgs> = forwardRef(
  (
    { as, disabled, onSelectOptions, selectedResults }: SelectActionArgs,
    ref
  ) => {
    // can't enforce this in the type because ContextActions can't conditionally render
    if (!onSelectOptions) throw new Error("onSelectOptions is required");
    const options: Array<SplitButtonOption> = onSelectOptions;

    const disabledHelp = match<void, string>([
      [() => disabled !== "", disabled],
      [() => true, ""],
    ])();

    return (
      <Observer>
        {() => (
          <ContextMenuAction
            options={options}
            icon={
              <Badge
                badgeContent={selectedResults.length}
                color="primary"
                max={100}
              >
                <SelectAllIcon />
              </Badge>
            }
            disabledHelp={disabledHelp}
            as={as}
            ref={ref}
          />
        )}
      </Observer>
    );
  }
);

SelectAction.displayName = "SelectAction";
export default SelectAction;
