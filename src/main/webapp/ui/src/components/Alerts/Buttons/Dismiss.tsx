import CloseIcon from "@mui/icons-material/Close";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import IconButtonWithTooltip from "../../IconButtonWithTooltip";

type DismissArgs = {
  onClose: () => void;
};

function Dismiss({ onClose }: DismissArgs): React.ReactNode {
  const { t } = useTranslation("common");
  return (
    <IconButtonWithTooltip
      title={t("actions.dismiss")}
      icon={<CloseIcon />}
      onClick={() => {
        onClose();
      }}
      data-test-id="toast-close"
      sx={{ m: 0.5 }}
    />
  );
}

/**
 * The dismiss button for the alert toasts.
 */
export default observer(Dismiss);
