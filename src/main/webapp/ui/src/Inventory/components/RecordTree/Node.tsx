import React, { useEffect, useContext } from "react";
import { Observer } from "mobx-react-lite";
import { TreeItem } from "@mui/x-tree-view/TreeItem";
import RecordTypeIcon from "../../../components/RecordTypeIcon";
import Avatar from "@mui/material/Avatar";
import CardHeader from "@mui/material/CardHeader";
import NameWithBadge from "../NameWithBadge";
import { match } from "../../../util/Util";
import NavigateToNode from "./NavigateToNode";
import SearchContext from "../../../stores/contexts/Search";
import { useTheme } from "@mui/material/styles";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";

type TreeNodeArgs = {
  node: InventoryRecord;
};

export default function TreeNode({ node }: TreeNodeArgs): React.ReactNode {
  const { search } = useContext(SearchContext);
  const theme = useTheme();

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
    match<void, React.ReactNode>([
      [
        () => Boolean(node.thumbnail),
        <img
          style={{ width: theme.spacing(5), maxHeight: "44px" }}
          src={node.thumbnail || ""}
          alt="Record thumbnail"
          key={2}
        />,
      ],
      [() => true, <RecordTypeIcon record={node} key={3} />],
    ])();

  return (
    <Observer>
      {() => (
        <TreeItem
          sx={{
            transition: theme.transitions.filterToggle,
            "& .MuiTreeItem-content": {
              cursor: "default",
            },
          }}
          itemId={node.globalId || ""}
          label={
            <CardHeader
              action={
                node.canNavigateToChildren && <NavigateToNode node={node} />
              }
              avatar={
                <Avatar sx={{ backgroundColor: "white", color: "black" }} variant="rounded">
                  {nodeAvatar()}
                </Avatar>
              }
              title={<NameWithBadge record={node} />}
              // TODO: requiredPermissions tooltips are not supported in tree view yet.
              sx={{
                p: 0.5,
                "& .MuiCardHeader-action": {
                  marginTop: "0 !important",
                  marginRight: "0 !important",
                },
                ...(search.alwaysFilterOut(node) ? { filter: "grayscale(1)", pointerEvents: "none", opacity: 0.6 } : {}),
              }}
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
