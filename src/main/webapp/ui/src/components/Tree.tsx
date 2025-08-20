import { SimpleTreeView } from "@mui/x-tree-view/SimpleTreeView";
import { TreeItem } from "@mui/x-tree-view/TreeItem";
import React from "react";

const TreeContext = React.createContext<{
  idMap: Map<string, unknown>;
  getId: ((t: unknown) => string) | null;
}>({
  idMap: new Map(),
  getId: null,
});

export function TreeNode({
  item,
  ...rest
}: React.ComponentProps<typeof TreeItem> & {
  item: unknown;
}): React.ReactNode {
  const { idMap, getId } = React.useContext(TreeContext);
  if (!getId) {
    throw new Error("TreeNode must be used within a TreeContext provider");
  }

  React.useEffect(() => {
    idMap.set(getId(item), item);
    return () => {
      idMap.delete(getId(item));
    };
  }, []);

  return <TreeItem {...rest} />;
}

export default function Tree({
  expandedItems,
  onExpandedItemsChange,
  selectedItems,
  onItemSelectionToggle,
  getId,
  ...rest
}: Omit<
  React.ComponentProps<typeof SimpleTreeView>,
  | "expandedItems"
  | "onExpandedItemsChange"
  | "selectedItems"
  | "onItemSelectionToggle"
> & {
  getId: (nodeModel: unknown) => string;
  expandedItems?: Array<unknown>;
  onExpandedItemsChange?: (
    event: React.SyntheticEvent,
    items: Array<unknown>,
  ) => void;
  selectedItems?: unknown | Array<unknown>;
  onItemSelectionToggle?: (
    event: React.SyntheticEvent,
    item: unknown | Array<unknown>,
  ) => void;
}): React.ReactNode {
  const [idMap] = React.useState<Map<string, unknown>>(new Map());
  return (
    <TreeContext.Provider
      value={{
        idMap,
        getId,
      }}
    >
      <SimpleTreeView
        {...(expandedItems
          ? {
              expandedItems: expandedItems.map((item) => getId(item)),
            }
          : {})}
        {...(onExpandedItemsChange
          ? {
              onExpandedItemsChange: (event, itemIds) => {
                onExpandedItemsChange(
                  event,
                  itemIds.map((id) => {
                    const item = idMap.get(id);
                    if (!item) {
                      throw new Error(
                        `Item with id ${id} has not previously been rendered as TreeNode`,
                      );
                    }
                    return item;
                  }),
                );
              },
            }
          : {})}
        {...(selectedItems
          ? {
              selectedItems: Array.isArray(selectedItems)
                ? selectedItems.map((item) => getId(item))
                : [getId(selectedItems)],
            }
          : {})}
        {...(onItemSelectionToggle
          ? {
              onItemSelectionToggle: (
                event,
                itemIds: string | Array<string>,
              ) => {
                onItemSelectionToggle(
                  event,
                  Array.isArray(itemIds)
                    ? itemIds.map((id) => {
                        const item = idMap.get(id);
                        if (!item) {
                          throw new Error(
                            `Item with id ${id} has not previously been rendered as TreeNode`,
                          );
                        }
                        return item;
                      })
                    : (() => {
                        const item = idMap.get(itemIds);
                        if (!item) {
                          throw new Error(
                            `Item with id ${itemIds} has not previously been rendered as TreeNode`,
                          );
                        }
                        return item;
                      })(),
                );
              },
            }
          : {})}
        {...rest}
      />
    </TreeContext.Provider>
  );
}
