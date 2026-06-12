import { SimpleTreeView } from "@mui/x-tree-view/SimpleTreeView";
import { getTreeItemUtilityClass, treeItemClasses } from "@mui/x-tree-view/TreeItem";
import { observer } from "mobx-react-lite";
import React, { type SyntheticEvent, useContext } from "react";
import SearchContext from "../../../stores/contexts/Search";
// biome-ignore lint/style/useImportType: initial biome migration
import { type GlobalId } from "../../../stores/definitions/BaseRecord";
import Node from "./Node";

const tappedToggleIcon = (event: SyntheticEvent) => !(event.target as HTMLElement).closest(`.${treeItemClasses.label}`);

function RecordTree(): React.ReactNode {
  const { search } = useContext(SearchContext);

  const handleToggle = (_: SyntheticEvent | null, nodeIds: Array<GlobalId>) => {
    search.tree.setExpanded(nodeIds);
  };

  const handleSelect = (event: SyntheticEvent | null, nodeIds: string | null) => {
    if (!event) return;
    if (!tappedToggleIcon(event) && nodeIds !== search.tree.selected) {
      search.tree.setSelected(nodeIds);
    }
  };

  return (
    <SimpleTreeView
      sx={{
        pt: 1,
        flexGrow: 1,
        // Tree View exposes no state-class constant; getTreeItemUtilityClass
        // resolves the global Mui-selected class.
        [`& .${getTreeItemUtilityClass("selected")} > .${treeItemClasses.content} .${treeItemClasses.label}`]: {
          backgroundColor: "rgba(0, 173, 239, 0.08) !important",
        },
      }}
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
