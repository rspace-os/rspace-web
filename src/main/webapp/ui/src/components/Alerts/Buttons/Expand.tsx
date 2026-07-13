import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import ExpandCollapseIcon from "../../ExpandCollapseIcon";
import IconButtonWithTooltip from "../../IconButtonWithTooltip";

type ExpandButtonArgs = {
  ariaLabel: string;
  expanded: boolean;
  setExpanded: (newExpanded: boolean) => void;
  size?: "small" | "medium" | "large";
};

function ExpandButton({ ariaLabel, expanded, setExpanded, size = "medium" }: ExpandButtonArgs): React.ReactNode {
  const { t } = useTranslation("common");
  return (
    <IconButtonWithTooltip
      ariaLabel={ariaLabel}
      title={expanded ? t("actions.collapse") : t("actions.expand")}
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
