import MenuOpenOutlinedIcon from "@mui/icons-material/MenuOpenOutlined";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import useStores from "../../../stores/use-stores";
import useNavigateHelpers from "../../useNavigateHelpers";

type NavigateToNodeArgs = {
  node: InventoryRecord;
};

function NavigateToNode({ node }: NavigateToNodeArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const { moveStore } = useStores();
  const { navigateToSearch } = useNavigateHelpers();

  if (moveStore.isMoving || node.children.length === 0) return null;
  return (
    <IconButtonWithTooltip
      sx={{ cursor: "pointer" }}
      title={t("recordTree.navigateToContainer")}
      icon={<MenuOpenOutlinedIcon />}
      onClick={(e: React.MouseEvent<HTMLButtonElement>) => {
        e.stopPropagation();
        navigateToSearch({ parentGlobalId: node.globalId });
      }}
    />
  );
}

export default observer(NavigateToNode);
