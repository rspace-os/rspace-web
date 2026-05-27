import { describe, expect, test, vi } from "vitest";
import React, { useState } from "react";
import userEvent from "@testing-library/user-event";
import "@testing-library/react";
import { render, screen } from "@/__tests__/customQueries";
import { ThemeProvider } from "@mui/material/styles";
import { Button, Box, Typography } from "@mui/material";
import theme from "../../theme";
import { Tree, TreeItem } from "../Tree";
type TreeItemData = {
  id: string;
  name: string;
  childItems?: TreeItemData[];
};
const sampleData: TreeItemData[] = [
  {
    id: "1",
    name: "Item 1",
  },
  {
    id: "2",
    name: "Item 2",
  },
  {
    id: "3",
    name: "Item 3",
  },
  {
    id: "4",
    name: "Item 4",
  },
];
const hierarchicalData: TreeItemData[] = [
  {
    id: "parent",
    name: "Parent Item",
    childItems: [
      {
        id: "child1",
        name: "Child Item 1",
      },
      {
        id: "child2",
        name: "Child Item 2",
      },
    ],
  },
  {
    id: "parent2",
    name: "Another Parent",
    childItems: [
      {
        id: "child3",
        name: "Child Item 3",
      },
      {
        id: "child4",
        name: "Child Item 4",
        childItems: [
          {
            id: "grandchild1",
            name: "Grandchild 1",
          },
          {
            id: "grandchild2",
            name: "Grandchild 2",
          },
        ],
      },
    ],
  },
];
const renderTreeItems = (items: TreeItemData[]): React.ReactNode => {
  return items.map((item) => (
    <TreeItem key={item.id} item={item} label={item.name}>
      {item.childItems && renderTreeItems(item.childItems)}
    </TreeItem>
  ));
};
const SimpleTreeExample = ({
  onSelectionChange = () => {},
}: {
  onSelectionChange?: (items: TreeItemData[]) => void;
}) => {
  const [selectedItem, setSelectedItem] = useState<TreeItemData | null>(null);
  const handleSelectionChange = (
    event: React.SyntheticEvent,
    item: TreeItemData | null,
  ) => {
    setSelectedItem(item);
    onSelectionChange(item ? [item] : []);
  };
  return (
    <ThemeProvider theme={theme}>
      <Box p={2}>
        <Typography variant="h5" gutterBottom>
          Simple Tree Selection
        </Typography>
        <Tree
          getId={(item) => item.id}
          selectedItems={selectedItem}
          onSelectedItemsChange={handleSelectionChange}
        >
          {renderTreeItems(sampleData)}
        </Tree>
        <Box mt={2}>
          <Typography variant="body2">
            Selected: {selectedItem?.name || "None"}
          </Typography>
        </Box>
      </Box>
    </ThemeProvider>
  );
};
const MultiSelectTreeExample = ({
  onSelectionChange = () => {},
}: {
  onSelectionChange?: (items: TreeItemData[]) => void;
}) => {
  const [selectedItems, setSelectedItems] = useState<TreeItemData[]>([]);
  const handleSelectionChange = (
    event: React.SyntheticEvent,
    items: TreeItemData[],
  ) => {
    setSelectedItems(items);
    onSelectionChange(items);
  };
  const clearSelection = () => {
    setSelectedItems([]);
    onSelectionChange([]);
  };
  return (
    <ThemeProvider theme={theme}>
      <Box p={2}>
        <Typography variant="h5" gutterBottom>
          Multi-Select Tree
        </Typography>
        <Box mb={2}>
          <Button onClick={clearSelection}>Clear Selection</Button>
        </Box>
        <Tree
          multiSelect
          getId={(item) => item.id}
          selectedItems={selectedItems}
          onSelectedItemsChange={handleSelectionChange}
        >
          {renderTreeItems(sampleData)}
        </Tree>
        <Box mt={2}>
          <Typography variant="body2">
            Selected:{" "}
            {selectedItems.map((item) => item.name).join(", ") || "None"}
          </Typography>
        </Box>
      </Box>
    </ThemeProvider>
  );
};
const ExpandableTreeExample = ({
  onExpansionChange = () => {},
}: {
  onExpansionChange?: (items: TreeItemData[]) => void;
}) => {
  const [expandedItems, setExpandedItems] = useState<TreeItemData[]>([]);
  const handleExpansionChange = (
    event: React.SyntheticEvent,
    items: TreeItemData[],
  ) => {
    setExpandedItems(items);
    onExpansionChange(items);
  };
  const expandAll = () => {
    const allExpandableItems: TreeItemData[] = [];
    const collectExpandableItems = (items: TreeItemData[]) => {
      items.forEach((item) => {
        if (item.childItems && item.childItems.length > 0) {
          allExpandableItems.push(item);
          collectExpandableItems(item.childItems);
        }
      });
    };
    collectExpandableItems(hierarchicalData);
    setExpandedItems(allExpandableItems);
    onExpansionChange(allExpandableItems);
  };
  const collapseAll = () => {
    setExpandedItems([]);
    onExpansionChange([]);
  };
  return (
    <ThemeProvider theme={theme}>
      <Box p={2}>
        <Typography variant="h5" gutterBottom>
          Expandable Tree
        </Typography>
        <Box mb={2} gap={1} display="flex">
          <Button onClick={expandAll}>Expand All</Button>
          <Button onClick={collapseAll}>Collapse All</Button>
        </Box>
        <Tree
          getId={(item) => item.id}
          expandedItems={expandedItems}
          onExpandedItemsChange={handleExpansionChange}
        >
          {renderTreeItems(hierarchicalData)}
        </Tree>
        <Box mt={2}>
          <Typography variant="body2">
            Expanded:{" "}
            {expandedItems.map((item) => item.name).join(", ") || "None"}
          </Typography>
        </Box>
      </Box>
    </ThemeProvider>
  );
};
const ControlledTreeExample = ({
  onSelectionChange = () => {},
  onExpansionChange = () => {},
}: {
  onSelectionChange?: (items: TreeItemData[]) => void;
  onExpansionChange?: (items: TreeItemData[]) => void;
}) => {
  const [selectedItem, setSelectedItem] = useState<TreeItemData | null>(null);
  const [expandedItems, setExpandedItems] = useState<TreeItemData[]>([]);
  const handleSelectionChange = (
    event: React.SyntheticEvent,
    item: TreeItemData | null,
  ) => {
    setSelectedItem(item);
    onSelectionChange(item ? [item] : []);
  };
  const handleExpansionChange = (
    event: React.SyntheticEvent,
    items: TreeItemData[],
  ) => {
    setExpandedItems(items);
    onExpansionChange(items);
  };
  const expandAll = () => {
    const allExpandableItems: TreeItemData[] = [];
    const collectExpandableItems = (items: TreeItemData[]) => {
      items.forEach((item) => {
        if (item.childItems && item.childItems.length > 0) {
          allExpandableItems.push(item);
          collectExpandableItems(item.childItems);
        }
      });
    };
    collectExpandableItems(hierarchicalData);
    setExpandedItems(allExpandableItems);
    onExpansionChange(allExpandableItems);
  };
  const collapseAll = () => {
    setExpandedItems([]);
    onExpansionChange([]);
  };
  const clearSelection = () => {
    setSelectedItem(null);
    onSelectionChange([]);
  };
  return (
    <ThemeProvider theme={theme}>
      <Box p={2}>
        <Typography variant="h5" gutterBottom>
          Controlled Tree (Selection + Expansion)
        </Typography>
        <Box mb={2} gap={1} display="flex">
          <Button onClick={expandAll}>Expand All</Button>
          <Button onClick={collapseAll}>Collapse All</Button>
          <Button onClick={clearSelection}>Clear Selection</Button>
        </Box>
        <Tree
          getId={(item) => item.id}
          selectedItems={selectedItem}
          onSelectedItemsChange={handleSelectionChange}
          expandedItems={expandedItems}
          onExpandedItemsChange={handleExpansionChange}
        >
          {renderTreeItems(hierarchicalData)}
        </Tree>
        <Box mt={2}>
          <Typography variant="body2">
            Selected: {selectedItem?.name || "None"}
          </Typography>
          <Typography variant="body2">
            Expanded:{" "}
            {expandedItems.map((item) => item.name).join(", ") || "None"}
          </Typography>
        </Box>
      </Box>
    </ThemeProvider>
  );
};
describe("Tree", () => {
  test("supports single selection and replaces the current selection", async () => {
    const user = userEvent.setup();
    const onSelectionChange = vi.fn();
    render(<SimpleTreeExample onSelectionChange={onSelectionChange} />);
    await user.click(screen.getByText("Item 1"));
    expect(onSelectionChange).toHaveBeenLastCalledWith([
      expect.objectContaining({
        name: "Item 1",
      }),
    ]);
    await user.click(screen.getByText("Item 2"));
    expect(onSelectionChange).toHaveBeenLastCalledWith([
      expect.objectContaining({
        name: "Item 2",
      }),
    ]);
  });
  test("supports multi-select and clearing the selection", async () => {
    const user = userEvent.setup();
    const onSelectionChange = vi.fn();
    render(<MultiSelectTreeExample onSelectionChange={onSelectionChange} />);
    await user.click(screen.getByText("Item 1"));
    await userEvent.click(screen.getByText("Item 2"));
    expect(onSelectionChange).toHaveBeenCalledTimes(2);
    await user.click(
      screen.getByRole("button", {
        name: /clear selection/i,
      }),
    );
    expect(onSelectionChange).toHaveBeenLastCalledWith([]);
  });
  test("expands and collapses items through the control buttons", async () => {
    const user = userEvent.setup();
    const onExpansionChange = vi.fn();
    render(<ExpandableTreeExample onExpansionChange={onExpansionChange} />);
    await user.click(
      screen.getByRole("button", {
        name: /expand all/i,
      }),
    );
    expect(onExpansionChange).toHaveBeenCalled();
    expect(onExpansionChange.mock.lastCall?.[0]).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          name: "Parent Item",
        }),
        expect.objectContaining({
          name: "Another Parent",
        }),
      ]),
    );
    await user.click(
      screen.getByRole("button", {
        name: /collapse all/i,
      }),
    );
    expect(onExpansionChange).toHaveBeenLastCalledWith([]);
  });
  test("supports controlled selection and expansion", async () => {
    const user = userEvent.setup();
    const onSelectionChange = vi.fn();
    const onExpansionChange = vi.fn();
    render(
      <ControlledTreeExample
        onSelectionChange={onSelectionChange}
        onExpansionChange={onExpansionChange}
      />,
    );
    await user.click(
      screen.getByRole("button", {
        name: /expand all/i,
      }),
    );
    expect(onExpansionChange).toHaveBeenCalled();
    await user.click(screen.getAllByText("Child Item 1")[0]);
    expect(onSelectionChange).toHaveBeenLastCalledWith([
      expect.objectContaining({
        name: "Child Item 1",
      }),
    ]);
  });
  test("supports keyboard navigation and selection", async () => {
    const user = userEvent.setup();
    const onSelectionChange = vi.fn();
    render(<SimpleTreeExample onSelectionChange={onSelectionChange} />);
    const item1 = screen.getByRole("treeitem", {
      name: /Item 1/i,
    });
    item1.focus();
    expect(item1).toHaveFocus();
    await user.keyboard("{ArrowDown}");
    expect(
      screen.getByRole("treeitem", {
        name: /Item 2/i,
      }),
    ).toHaveFocus();
    await user.keyboard(" ");
    expect(onSelectionChange).toHaveBeenCalled();
  });
  test("is accessible", async () => {
    const { baseElement } = render(<SimpleTreeExample />);

    // @ts-expect-error toBeAccessible is provided by @sa11y/vitest
    // eslint-disable-next-line @typescript-eslint/no-unsafe-call
    await expect(baseElement).toBeAccessible();
  });
});
