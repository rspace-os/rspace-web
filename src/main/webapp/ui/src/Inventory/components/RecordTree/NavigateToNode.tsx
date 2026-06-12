import MenuOpenOutlinedIcon from "@mui/icons-material/MenuOpenOutlined";
import { observer } from "mobx-react-lite";
import type React from "react";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";
// biome-ignore lint/style/useImportType: initial biome migration
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import useStores from "../../../stores/use-stores";
import useNavigateHelpers from "../../useNavigateHelpers";

type NavigateToNodeArgs = {
  node: InventoryRecord;
};

function NavigateToNode({ node }: NavigateToNodeArgs): React.ReactNode {
  const { moveStore } = useStores();
  const { navigateToSearch } = useNavigateHelpers();

  // biome-ignore lint/complexity/noExtraBooleanCast: initial biome migration
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
