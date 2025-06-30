import React, { useContext, type SyntheticEvent } from "react";
import { makeStyles } from "tss-react/mui";
import { SimpleTreeView } from "@mui/x-tree-view/SimpleTreeView";
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

const tappedToggleIcon = (event: SyntheticEvent) =>
  !(event.target as HTMLElement).closest(".MuiTreeItem-label");

function RecordTree(): React.ReactNode {
  const { search } = useContext(SearchContext);
  const { classes } = useStyles();

  const handleToggle = (_: SyntheticEvent, nodeIds: Array<GlobalId>) => {
    search.tree.setExpanded(nodeIds);
  };

  const handleSelect = (event: SyntheticEvent, nodeIds: string | null) => {
    if (!tappedToggleIcon(event) && nodeIds !== search.tree.selected) {
      search.tree.setSelected(nodeIds);
    }
  };

  return (
    <SimpleTreeView
      className={classes.root}
      expandedItems={search.tree.expanded}
      selectedItems={search.tree.selected}
      onExpandedItemsChange={handleToggle}
      onSelectedItemsChange={handleSelect}
    >
      {search.filteredResults.map((node) => (
        <Node key={node.globalId ?? null} node={node} />
      ))}
    </SimpleTreeView>
  );
}

export default observer(RecordTree);
