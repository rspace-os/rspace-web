import ScienceIcon from "@mui/icons-material/Science";
import type MenuItem from "@mui/material/MenuItem";
import { Observer } from "mobx-react-lite";
import React, { forwardRef } from "react";
import { useTranslation } from "react-i18next";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import SubSampleModel from "../../../stores/models/SubSampleModel";
import { match } from "../../../util/Util";
import OperationWizard from "../Operations/OperationWizard";
import ContextMenuAction, { type ContextMenuRenderOptions } from "./ContextMenuAction";

type ProcessActionArgs = {
  as: ContextMenuRenderOptions;
  disabled: string;
  selectedResults: Array<InventoryRecord>;
  closeMenu: () => void;
};

/**
 * Launches the operation wizard on a single selected subsample (RSDEV-1231). Shown only for exactly
 * one subsample; disabled otherwise.
 */
const ProcessAction = forwardRef<React.ElementRef<typeof MenuItem>, ProcessActionArgs>(
  ({ as, disabled, selectedResults, closeMenu }, ref) => {
    const { t } = useTranslation("inventory");
    const [open, setOpen] = React.useState(false);
    const origin = selectedResults[0];

    const disabledHelp = match<void, string>([
      [() => disabled !== "", disabled],
      [() => selectedResults.length !== 1, t("operations.action.singleSubsampleOnly")],
      [() => !(origin instanceof SubSampleModel), t("operations.action.subsampleOnly")],
      [() => true, ""],
    ])();

    const onCloseHandler = () => {
      setOpen(false);
      closeMenu();
    };

    return (
      <Observer>
        {() => (
          <ContextMenuAction
            onClick={() => setOpen(true)}
            icon={<ScienceIcon />}
            label={t("operations.action.process")}
            disabledHelp={disabledHelp}
            as={as}
            ref={ref}
          >
            {origin instanceof SubSampleModel ? (
              <OperationWizard key={open ? 1 : 0} open={open} onClose={onCloseHandler} origin={origin} />
            ) : null}
          </ContextMenuAction>
        )}
      </Observer>
    );
  },
);

ProcessAction.displayName = "ProcessAction";
export default ProcessAction;
