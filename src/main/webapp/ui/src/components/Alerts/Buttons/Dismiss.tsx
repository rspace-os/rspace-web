import React from "react";
import { observer } from "mobx-react-lite";
import IconButtonWithTooltip from "../../IconButtonWithTooltip";
import CloseIcon from "@mui/icons-material/Close";

type DismissArgs = {
  onClose: () => void;
};

function Dismiss({ onClose }: DismissArgs): React.ReactNode {
  return (
    <IconButtonWithTooltip
      title="Dismiss"
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
