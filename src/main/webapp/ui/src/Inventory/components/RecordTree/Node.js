//@flow

import React, {
  useEffect,
  useContext,
  type Node,
  type ElementProps,
} from "react";
import { Observer } from "mobx-react-lite";
import TreeItem from "@mui/lab/TreeItem";
import RecordTypeIcon from "../../../components/RecordTypeIcon";
import { withStyles } from "Styles";
import { makeStyles } from "tss-react/mui";
import Avatar from "@mui/material/Avatar";
import CardHeader from "@mui/material/CardHeader";
import NameWithBadge from "../NameWithBadge";
import { match } from "../../../util/Util";
import NavigateToNode from "./NavigateToNode";
import SearchContext from "../../../stores/contexts/Search";
import { globalStyles } from "../../../theme";
import clsx from "clsx";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";

const CustomTreeItem = withStyles<
  ElementProps<typeof TreeItem>,
  { root: string, content: string }
>((theme) => ({
  root: {
    transition: theme.transitions.filterToggle,
  },
  content: {
    cursor: "default",
  },
}))(TreeItem);

const useStyles = makeStyles()((theme) => ({
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

type TreeNodeArgs = {|
  node: InventoryRecord,
|};

export default function TreeNode({ node }: TreeNodeArgs): Node {
  const { search } = useContext(SearchContext);
  const { classes } = useStyles();
  const { classes: globalClasses } = globalStyles();

  useEffect(() => {
    if (!node.infoLoaded) node.loadChildren();
  }, []);

  const children = () => {
    const filteredChildren: Array<InventoryRecord> = node.children.filter(
      (child) => search.tree.filteredTypes.includes(child.recordType)
    );

    return filteredChildren.map((child: InventoryRecord) => (
      <TreeNode key={child.globalId} node={child} />
    ));
  };

  const nodeAvatar = () =>
    match<void, Node>([
      [
        () => Boolean(node.thumbnail),
        <img className={classes.image} src={node.thumbnail} key={2} />,
      ],
      [() => true, <RecordTypeIcon record={node} key={3} />],
    ])();

  return (
    <Observer>
      {() => (
        <CustomTreeItem
          nodeId={node.globalId}
          label={
            <CardHeader
              action={
                node.canNavigateToChildren && <NavigateToNode node={node} />
              }
              avatar={
                <Avatar className={classes.avatar} variant="rounded">
                  {nodeAvatar()}
                </Avatar>
              }
              title={<NameWithBadge record={node} />}
              className={clsx(
                classes.cardHeader,
                search.alwaysFilterOut(node) && globalClasses.greyOut
              )}
              data-testid={`recordTreeNode_${node.globalId ?? "NEW"}`}
            />
          }
        >
          {children()}
        </CustomTreeItem>
      )}
    </Observer>
  );
}
