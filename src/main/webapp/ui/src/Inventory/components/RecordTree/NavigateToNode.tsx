import React from "react";
import MenuOpenOutlinedIcon from "@mui/icons-material/MenuOpenOutlined";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";
import useStores from "../../../stores/use-stores";
import { observer } from "mobx-react-lite";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import useNavigateHelpers from "../../useNavigateHelpers";

type NavigateToNodeArgs = {
  node: InventoryRecord;
};

function NavigateToNode({ node }: NavigateToNodeArgs): React.ReactNode {
  const { moveStore } = useStores();
  const { navigateToSearch } = useNavigateHelpers();

  if (moveStore.isMoving || Boolean(node.children.length === 0)) return null;
  return (
    <IconButtonWithTooltip
      sx={{ cursor: "pointer" }}
      title="Navigate to container"
      icon={<MenuOpenOutlinedIcon />}
      onClick={(e: React.MouseEvent<HTMLButtonElement>) => {
        e.stopPropagation();
        navigateToSearch({ parentGlobalId: node.globalId });
      }}
    />
  );
}

export default observer(NavigateToNode);
