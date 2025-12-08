import Avatar from "@mui/material/Avatar";
import CardHeader from "@mui/material/CardHeader";
import { TreeItem } from "@mui/x-tree-view/TreeItem";
import clsx from "clsx";
import { Observer } from "mobx-react-lite";
import type React from "react";
import { useContext, useEffect } from "react";
import { makeStyles } from "tss-react/mui";
import RecordTypeIcon from "../../../components/RecordTypeIcon";
import SearchContext from "../../../stores/contexts/Search";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import { globalStyles } from "../../../theme";
import { match } from "../../../util/Util";
import NameWithBadge from "../NameWithBadge";
import NavigateToNode from "./NavigateToNode";

const useStyles = makeStyles()((theme) => ({
    treeItem: {
        transition: theme.transitions.filterToggle,
    },
    treeItemContent: {
        cursor: "default",
    },
    avatar: {
        backgroundColor: "white",
        color: "black",
    },
    image: {
        width: theme.spacing(5),
        maxHeight: "44px",
    },
    cardHeader: {
        padding: theme.spacing(0.5),
        "& .MuiCardHeader-action": {
            marginTop: "0 !important",
            marginRight: "0 !important",
        },
    },
}));

type TreeNodeArgs = {
    node: InventoryRecord;
};

export default function TreeNode({ node }: TreeNodeArgs): React.ReactNode {
    const { search } = useContext(SearchContext);
    const { classes } = useStyles();
    const { classes: globalClasses } = globalStyles();

    useEffect(() => {
        if (!node.infoLoaded) node.loadChildren();
    }, [node.infoLoaded, node.loadChildren]);

    const children = () => {
        const filteredChildren: Array<InventoryRecord> = node.children.filter((child) =>
            search.tree.filteredTypes.includes(child.recordType),
        );

        return filteredChildren.map((child: InventoryRecord) => <TreeNode key={child.globalId} node={child} />);
    };

    const nodeAvatar = () =>
        match<void, React.ReactNode>([
            [
                () => Boolean(node.thumbnail),
                <img className={classes.image} src={node.thumbnail || ""} alt="Record thumbnail" key={2} />,
            ],
            [() => true, <RecordTypeIcon record={node} key={3} />],
        ])();

    return (
        <Observer>
            {() => (
                <TreeItem
                    className={classes.treeItem}
                    classes={{
                        content: classes.treeItemContent,
                    }}
                    itemId={node.globalId || ""}
                    label={
                        <CardHeader
                            action={node.canNavigateToChildren && <NavigateToNode node={node} />}
                            avatar={
                                <Avatar className={classes.avatar} variant="rounded">
                                    {nodeAvatar()}
                                </Avatar>
                            }
                            title={<NameWithBadge record={node} />}
                            className={clsx(classes.cardHeader, search.alwaysFilterOut(node) && globalClasses.greyOut)}
                            data-testid={`recordTreeNode_${node.globalId ?? "NEW"}`}
                        />
                    }
                >
                    {children()}
                </TreeItem>
            )}
        </Observer>
    );
}
