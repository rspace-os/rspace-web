import { faCodeBranch } from "@fortawesome/free-solid-svg-icons/faCodeBranch";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import type MenuItem from "@mui/material/MenuItem";
import { Observer } from "mobx-react-lite";
import type React from "react";
import { forwardRef } from "react";
import { useTranslation } from "react-i18next";
import type { InventoryRecord } from "@/stores/definitions/InventoryRecord";
import SubSampleModel from "@/stores/models/SubSampleModel";
import { isProcessableSelection, useOperationWizardLauncher } from "../Operations/useOperationWizardLauncher";
import ContextMenuAction, { type ContextMenuRenderOptions } from "./ContextMenuAction";

type ProcessActionArgs = {
  as: ContextMenuRenderOptions;
  disabled: string;
  selectedResults: Array<InventoryRecord>;
  closeMenu: () => void;
};

const ProcessAction = forwardRef<React.ElementRef<typeof MenuItem>, ProcessActionArgs>(
  ({ as, disabled, selectedResults, closeMenu }, ref) => {
    const { t } = useTranslation("inventory");
    const origins = selectedResults.filter((r): r is SubSampleModel => r instanceof SubSampleModel);
    const { launch, wizard } = useOperationWizardLauncher(origins, { onClose: closeMenu });

    return (
      <Observer>
        {() => (
          <ContextMenuAction
            onClick={() => void launch()}
            icon={<FontAwesomeIcon icon={faCodeBranch} size="lg" />}
            label={t("operations.action.process")}
            disabledHelp={disabled}
            as={as}
            ref={ref}
          >
            {isProcessableSelection(selectedResults) ? wizard : null}
          </ContextMenuAction>
        )}
      </Observer>
    );
  },
);

ProcessAction.displayName = "ProcessAction";
export default ProcessAction;
