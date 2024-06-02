// @flow

import React, {
  useContext,
  type Node as ReactNode,
  type ComponentType,
} from "react";
import { makeStyles } from "tss-react/mui";
import TreeView from "@mui/lab/TreeView";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import { observer } from "mobx-react-lite";
import Node from "./Node";
import SearchContext from "../../../stores/contexts/Search";
import { type GlobalId } from "../../../stores/definitions/BaseRecord";

const useStyles = makeStyles()((theme) => ({
  root: {
    paddingTop: theme.spacing(1),
    flexGrow: 1,
    "& .Mui-selected > .MuiTreeItem-content .MuiTreeItem-label": {
      backgroundColor: "rgba(0, 173, 239, 0.08) !important",
    },
  },
  header: {
    display: "flex",
    paddingLeft: "20px",
    "& tr": {
      display: "flex",
      width: "100%",
      "& th": {
        flexGrow: 1,
      },
      "& th:nth-of-type(1)": {
        paddingLeft: "75px",
      },
    },
  },
  table: {
    width: "100%",
  },
  openCloseIcon: {
    fontSize: "2rem",
  },
}));

const tappedToggleIcon = (event: Event) =>
  // $FlowExpectedError[prop-missing] This is a hack
  !event.target.closest(".MuiTreeItem-label");

function RecordTree(): ReactNode {
  const { search } = useContext(SearchContext);
  const { classes } = useStyles();

  const handleToggle = (_: mixed, nodeIds: Array<GlobalId>) => {
    search.tree.setExpanded(nodeIds);
  };

  const handleSelect = (event: Event, nodeIds: GlobalId) => {
    if (!tappedToggleIcon(event) && nodeIds !== search.tree.selected) {
      search.tree.setSelected(nodeIds);
    }
  };

  return (
    <TreeView
      className={classes.root}
      defaultCollapseIcon={<ExpandMoreIcon className={classes.openCloseIcon} />}
      defaultExpandIcon={<ChevronRightIcon className={classes.openCloseIcon} />}
      expanded={search.tree.expanded}
      selected={[search.tree.selected]}
      onNodeToggle={handleToggle}
      onNodeSelect={handleSelect}
    >
      {search.filteredResults.map((node) => (
        <Node key={node.globalId ?? null} node={node} />
      ))}
    </TreeView>
  );
}

export default (observer(RecordTree): ComponentType<{||}>);
