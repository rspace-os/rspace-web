// @flow strict

import React, { type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import IconButtonWithTooltip from "../../IconButtonWithTooltip";
import CloseIcon from "@mui/icons-material/Close";
import { type Alert } from "../../../stores/contexts/Alert";

type DismissArgs = {|
  onClose: () => void,
  alert: Alert,
|};

function Dismiss({ onClose, alert }: DismissArgs): Node {
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

export default (observer(Dismiss): ComponentType<DismissArgs>);
