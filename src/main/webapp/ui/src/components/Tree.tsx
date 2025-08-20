import { SimpleTreeView } from "@mui/x-tree-view/SimpleTreeView";
import { TreeItem } from "@mui/x-tree-view/TreeItem";
import React from "react";

/*
 * TreeContext is intentionally untyped (uses unknown) because React contexts cannot be generic.
 * This forces us to use type assertions in useTreeContext and TreeProvider to bridge the gap
 * between the untyped context and our generic components. The casting is safe because:
 * 1. TreeProvider ensures only properly typed values enter the context
 * 2. useTreeContext is only used within components that have the correct generic types
 * 3. The Tree component controls the lifecycle and ensures type consistency
 */
const TreeContext = React.createContext<{
  idMap: Map<string, unknown>;
  getId: ((t: unknown) => string) | null;
}>({
  idMap: new Map(),
  getId: null,
});

/*
 * useTreeContext provides type-safe access to the TreeContext by casting the untyped context
 * to the expected generic types. This casting is necessary due to React context limitations
 * but is safe when used within the Tree component ecosystem.
 */
function useTreeContext<Item, Id extends string>(): {
  idMap: Map<Id, Item>;
  getId: ((t: Item) => Id) | null;
} {
  const context = React.useContext(TreeContext);
  return context as {
    idMap: Map<Id, Item>;
    getId: ((t: Item) => Id) | null;
  };
}

/*
 * TreeProvider abstracts the type casting required to bridge our generic types
 * with the untyped TreeContext. This casting is safe because we control both
 * the input (properly typed) and consumption (through useTreeContext).
 */
function TreeProvider<Item, Id extends string>({
  idMap,
  getId,
  children,
}: {
  idMap: Map<Id, Item>;
  getId: (item: Item) => Id;
  children: React.ReactNode;
}): React.ReactNode {
  return (
    <TreeContext.Provider
      value={{
        idMap: idMap as Map<string, unknown>,
        getId: getId as (t: unknown) => string,
      }}
    >
      {children}
    </TreeContext.Provider>
  );
}

/**
 * A tree node component that automatically manages the relationship between your data items
 * and their visual representation in the tree. Each TreeNode registers itself with the parent
 * Tree component so that callbacks receive your original data objects instead of just IDs.
 *
 * @param item The data object this node represents
 * @param rest All other props are passed through to the underlying MUI TreeItem
 *
 * @example
 * ```tsx
 * type File = { id: string; name: string; };
 *
 * <TreeNode<File, string>
 *   item={{ id: "file-1", name: "document.txt" }}
 *   label="document.txt"
 * />
 * ```
 */
export function TreeNode<Item, Id extends string>({
  item,
  ...rest
}: Omit<React.ComponentProps<typeof TreeItem>, "itemId"> & {
  item: Item;
}): React.ReactNode {
  const { idMap, getId } = useTreeContext<Item, Id>();
  if (!getId) {
    throw new Error("TreeNode must be used within a TreeContext provider");
  }

  React.useEffect(() => {
    idMap.set(getId(item), item);
    return () => {
      idMap.delete(getId(item));
    };
  }, []);

  return <TreeItem itemId={getId(item)} {...rest} />;
}

/**
 * A generic tree component that works with your data objects directly, eliminating the need
 * to manually manage ID-to-object mappings. You provide a function to extract IDs from your
 * data, and all callbacks receive your original data objects.
 *
 * @param getId                 Function to extract a unique ID from each data item. This is
 *                              used internally to map between MUI's string-based IDs and your data objects.
 * @param expandedItems         Array of data items that should be expanded (optional)
 * @param onExpandedItemsChange Callback when expanded items change, receives data objects
 * @param selectedItems         Single data item or array of items that should be selected
 *                              selected (optional). Can be single item for single selection or array for
 *                              multi-selection.
 * @param onItemSelectionToggle Callback when selection changes, receives data objects.
 *                              Returns single item or array depending on selection mode.
 * @param rest                  All other props are passed through to the underlying MUI SimpleTreeView
 *
 * @example
 * ```tsx
 * type File = { id: string; name: string; type: 'file' | 'folder' };
 *
 * <Tree<File, string>
 *   getId={(file) => file.id}
 *   selectedItems={selectedFile}
 *   onItemSelectionToggle={(event, item) => setSelectedFile(item)}
 * >
 *   {files.map(file => (
 *     <TreeNode key={file.id} item={file} label={file.name} />
 *   ))}
 * </Tree>
 * ```
 */
export default function Tree<
  Item,
  Id extends string,
  MultiSelect extends boolean = false,
>({
  multiSelect,
  expandedItems,
  onExpandedItemsChange,
  selectedItems,
  onItemSelectionToggle,
  getId,
  ...rest
}: Omit<
  React.ComponentProps<typeof SimpleTreeView>,
  | "multiSelect"
  | "expandedItems"
  | "onExpandedItemsChange"
  | "selectedItems"
  | "onItemSelectionToggle"
  | "defaultSelectedItems"
> & {
  getId: (item: Item) => Id;
  multiSelect?: MultiSelect;
  expandedItems?: Array<Item>;
  onExpandedItemsChange?: (
    event: React.SyntheticEvent,
    items: Array<Item>,
  ) => void;
  selectedItems?: MultiSelect extends true ? Array<Item> : Item | null;
  onItemSelectionToggle?: (
    event: React.SyntheticEvent,
    item: MultiSelect extends true ? Array<Item> : Item | null,
  ) => void;
}): React.ReactNode {
  const [idMap] = React.useState<Map<Id, Item>>(new Map());
  return (
    <TreeProvider idMap={idMap} getId={getId}>
      <SimpleTreeView
        multiSelect={multiSelect}
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
                    const item = idMap.get(id as Id);
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
              selectedItems: (multiSelect && Array.isArray(selectedItems)
                ? (selectedItems as Array<Item>).map((item) => getId(item))
                : (selectedItems as Item | null) === null
                  ? ""
                  : getId(selectedItems as Item)) as MultiSelect extends true
                ? Array<string>
                : string,
            }
          : {})}
        {...(onItemSelectionToggle
          ? {
              onItemSelectionToggle: (event, itemIds) => {
                onItemSelectionToggle(
                  event,
                  Array.isArray(itemIds as any)
                    ? (itemIds as any).map((id: any) => {
                        const item = idMap.get(id);
                        if (!item) {
                          throw new Error(
                            `Item with id ${id} has not previously been rendered as TreeNode`,
                          );
                        }
                        return item;
                      })
                    : (() => {
                        const item = idMap.get(itemIds as Id);
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
    </TreeProvider>
  );
}
