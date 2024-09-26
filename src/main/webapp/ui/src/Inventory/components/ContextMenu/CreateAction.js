//@flow

import React, { type ComponentType, forwardRef } from "react";
import ContextMenuAction, {
  type ContextMenuRenderOptions,
} from "./ContextMenuAction";
import useStores from "../../../stores/use-stores";
import { Observer } from "mobx-react-lite";
import AddBoxIcon from "@mui/icons-material/AddBox";
import CreateDialog from "./CreateDialog";
import FromSampleDialog from "../../Template/FromSampleDialog";
import { match } from "../../../util/Util";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import ContainerModel from "../../../stores/models/ContainerModel";
import SampleModel from "../../../stores/models/SampleModel";
import TemplateModel from "../../../stores/models/TemplateModel";
import { menuIDs } from "../../../util/menuIDs";

type CreateActionArgs = {|
  as: ContextMenuRenderOptions,
  disabled: string,
  selectedResults: Array<InventoryRecord>,
  menuID: $Values<typeof menuIDs>,
  closeMenu: () => void,
|};

const CreateAction: ComponentType<CreateActionArgs> = forwardRef(
  (
    { as, disabled, selectedResults, menuID, closeMenu }: CreateActionArgs,
    ref
  ) => {
    const { createStore } = useStores();

    const isFullContainer = (): boolean =>
      selectedResults[0] instanceof ContainerModel && selectedResults[0].isFull;

    /* prevent multiple CreateDialog renders (or Dialog with wrong record) */
    const contextMatches = (): boolean =>
      createStore.creationContext === menuID ||
      createStore.creationContext === "containerLocation";

    const contextForTemplateMatches = (): boolean =>
      createStore.templateCreationContext === menuID;

    const canCreate: boolean =
      selectedResults.length === 1 && !isFullContainer();

    const canCreateTemplate: boolean =
      selectedResults[0] instanceof SampleModel &&
      !(selectedResults[0] instanceof TemplateModel);

    const onClick = () => {
      createStore.setCreationContext(menuID);
    };

    const closeDialog = async () => {
      if (selectedResults[0] instanceof ContainerModel)
        selectedResults[0].toggleAllLocations(false);
      createStore.setCreationContext("");
    };

    const onCloseHandler = () => {
      void closeDialog();
      closeMenu();
    };

    const fromSampleDialogCloseHandler = () => {
      createStore.setTemplateCreationContext("");
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
            {canCreate && contextMatches() && (
              <CreateDialog
                open={contextMatches()}
                onClose={onCloseHandler}
                existingRecord={selectedResults[0]}
              />
            )}
            {canCreateTemplate && contextForTemplateMatches() && (
              <FromSampleDialog
                open={contextForTemplateMatches()}
                // $FlowExpectedError[incompatible-type] if canCreateTemplate, result is a sample (not a template)
                sample={selectedResults[0]}
                onCancel={fromSampleDialogCloseHandler}
                onSubmit={fromSampleDialogCloseHandler}
              />
            )}
          </ContextMenuAction>
        )}
      </Observer>
    );
  }
);

CreateAction.displayName = "CreateAction";
export default CreateAction;
