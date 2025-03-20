// @flow strict

import React from "react";
import { observer } from "mobx-react-lite";
import IconButtonWithTooltip from "../../IconButtonWithTooltip";
import ExpandCollapseIcon from "../../ExpandCollapseIcon";

type ExpandButtonArgs = {
  ariaLabel: string;
  expanded: boolean;
  setExpanded: (newExpanded: boolean) => void;
  size?: "small" | "medium" | "large";
};

function ExpandButton({
  ariaLabel: ariaLabel,
  expanded,
  setExpanded,
  size = "medium",
}: ExpandButtonArgs): React.ReactNode {
  return (
    <IconButtonWithTooltip
      ariaLabel={ariaLabel}
      title={expanded ? "Collapse" : "Expand"}
      icon={<ExpandCollapseIcon open={expanded} />}
      onClick={() => setExpanded(!expanded)}
      data-test-id="toast-expand"
      size={size}
      sx={{ m: 0.5 }}
    />
  );
}

/**
 * The button on the alert toasts for expanding and collapsing the details
 * section.
 */
export default observer(ExpandButton);
