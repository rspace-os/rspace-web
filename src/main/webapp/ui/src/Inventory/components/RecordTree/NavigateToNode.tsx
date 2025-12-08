import MenuOpenOutlinedIcon from "@mui/icons-material/MenuOpenOutlined";
import { observer } from "mobx-react-lite";
import type React from "react";
import { makeStyles } from "tss-react/mui";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import useStores from "../../../stores/use-stores";
import useNavigateHelpers from "../../useNavigateHelpers";

type NavigateToNodeArgs = {
    node: InventoryRecord;
};

const useStyles = makeStyles()(() => ({
    icon: {
        cursor: "pointer",
    },
}));

function NavigateToNode({ node }: NavigateToNodeArgs): React.ReactNode {
    const { moveStore } = useStores();
    const { classes } = useStyles();
    const { navigateToSearch } = useNavigateHelpers();

    if (moveStore.isMoving || Boolean(node.children.length === 0)) return null;
    return (
        <IconButtonWithTooltip
            className={classes.icon}
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
