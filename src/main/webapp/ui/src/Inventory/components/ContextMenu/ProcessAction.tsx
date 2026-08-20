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
 * Launches the operation wizard on the selected subsamples (RSDEV-1231). Shown for one or more
 * subsamples; the wizard's picker then enables single-origin operations for a single selection and
 * Pool for two or more (adr/0007). Disabled when any selected record is not a subsample.
 */
const ProcessAction = forwardRef<React.ElementRef<typeof MenuItem>, ProcessActionArgs>(
  ({ as, disabled, selectedResults, closeMenu }, ref) => {
    const { t } = useTranslation("inventory");
    const [open, setOpen] = React.useState(false);
    const origins = selectedResults.filter((r): r is SubSampleModel => r instanceof SubSampleModel);
    const allSubsamples = origins.length >= 1 && origins.length === selectedResults.length;

    const disabledHelp = match<void, string>([
      [() => disabled !== "", disabled],
      [() => !allSubsamples, t("operations.action.subsampleOnly")],
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
            {allSubsamples ? (
              <OperationWizard key={open ? 1 : 0} open={open} onClose={onCloseHandler} origins={origins} />
            ) : null}
          </ContextMenuAction>
        )}
      </Observer>
    );
  },
);

ProcessAction.displayName = "ProcessAction";
export default ProcessAction;
