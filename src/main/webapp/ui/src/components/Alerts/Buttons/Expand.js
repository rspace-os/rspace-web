// @flow strict

import React, { type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import IconButtonWithTooltip from "../../IconButtonWithTooltip";
import ExpandCollapseIcon from "../../ExpandCollapseIcon";

type ExpandButtonArgs = {|
  ariaLabel: string,
  expanded: boolean,
  setExpanded: (boolean) => void,
  size?: "small" | "medium" | "large",
|};

function ExpandButton({
  ariaLabel: ariaLabel,
  expanded,
  setExpanded,
  size = "medium",
}: ExpandButtonArgs): Node {
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

export default (observer(ExpandButton): ComponentType<ExpandButtonArgs>);
