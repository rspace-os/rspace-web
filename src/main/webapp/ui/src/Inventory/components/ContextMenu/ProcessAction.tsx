import { faCodeBranch } from "@fortawesome/free-solid-svg-icons/faCodeBranch";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import type MenuItem from "@mui/material/MenuItem";
import { Observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import type SubSampleModel from "@/stores/models/SubSampleModel";
import { useOperationWizardLauncher } from "../Operations/useOperationWizardLauncher";
import ContextMenuAction, { type ContextMenuRenderOptions } from "./ContextMenuAction";

type ProcessActionArgs = {
  as: ContextMenuRenderOptions;
  disabled: string;
  selectedResults: Array<SubSampleModel>;
  closeMenu: () => void;
};

export default function ProcessAction({
  as,
  disabled,
  selectedResults,
  closeMenu,
  ref,
}: ProcessActionArgs & { ref?: React.Ref<React.ElementRef<typeof MenuItem>> }): React.ReactNode {
  const { t } = useTranslation("inventory");
  const { launch, wizard } = useOperationWizardLauncher(selectedResults, { onClose: closeMenu });

  return (
    <Observer>
      {() => (
        <ContextMenuAction
          onClick={() => void launch()}
          icon={<FontAwesomeIcon icon={faCodeBranch} size="lg" />}
          label={t("operations.action.process")}
          disabledHelp={disabled || (selectedResults.every((r) => r.canEdit) ? "" : t("contextMenu.edit.noPermission"))}
          as={as}
          ref={ref}
        >
          {wizard}
        </ContextMenuAction>
      )}
    </Observer>
  );
}
