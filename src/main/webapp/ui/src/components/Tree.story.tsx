import { Box, Button, Typography } from "@mui/material";
import { ThemeProvider } from "@mui/material/styles";
import type React from "react";
import { useState } from "react";
import theme from "../theme";
import { Tree, TreeItem } from "./Tree";

type TreeItemData = {
    id: string;
    name: string;
    children?: TreeItemData[];
};

const sampleData: TreeItemData[] = [
    { id: "1", name: "Item 1" },
    { id: "2", name: "Item 2" },
    { id: "3", name: "Item 3" },
    { id: "4", name: "Item 4" },
];

const hierarchicalData: TreeItemData[] = [
    {
        id: "parent",
        name: "Parent Item",
        children: [
            { id: "child1", name: "Child Item 1" },
            { id: "child2", name: "Child Item 2" },
        ],
    },
    {
        id: "parent2",
        name: "Another Parent",
        children: [
            { id: "child3", name: "Child Item 3" },
            {
                id: "child4",
                name: "Child Item 4",
                children: [
                    { id: "grandchild1", name: "Grandchild 1" },
                    { id: "grandchild2", name: "Grandchild 2" },
                ],
            },
        ],
    },
];

const renderTreeItems = (items: TreeItemData[]): React.ReactNode => {
    return items.map((item) => (
        <TreeItem key={item.id} item={item} label={item.name}>
            {item.children && renderTreeItems(item.children)}
        </TreeItem>
    ));
};

export const SimpleTreeExample = ({
    onSelectionChange = () => {},
}: {
    onSelectionChange?: (items: TreeItemData[]) => void;
}) => {
    const [selectedItem, setSelectedItem] = useState<TreeItemData | null>(null);

    const handleSelectionChange = (_event: React.SyntheticEvent, item: TreeItemData | null) => {
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
                    <Typography variant="body2">Selected: {selectedItem?.name || "None"}</Typography>
                </Box>
            </Box>
        </ThemeProvider>
    );
};

export const MultiSelectTreeExample = ({
    onSelectionChange = () => {},
}: {
    onSelectionChange?: (items: TreeItemData[]) => void;
}) => {
    const [selectedItems, setSelectedItems] = useState<TreeItemData[]>([]);

    const handleSelectionChange = (_event: React.SyntheticEvent, items: TreeItemData[]) => {
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
                        Selected: {selectedItems.map((item) => item.name).join(", ") || "None"}
                    </Typography>
                </Box>
            </Box>
        </ThemeProvider>
    );
};

export const ExpandableTreeExample = ({
    onExpansionChange = () => {},
}: {
    onExpansionChange?: (items: TreeItemData[]) => void;
}) => {
    const [expandedItems, setExpandedItems] = useState<TreeItemData[]>([]);

    const handleExpansionChange = (_event: React.SyntheticEvent, items: TreeItemData[]) => {
        setExpandedItems(items);
        onExpansionChange(items);
    };

    const expandAll = () => {
        const allExpandableItems: TreeItemData[] = [];
        const collectExpandableItems = (items: TreeItemData[]) => {
            items.forEach((item) => {
                if (item.children && item.children.length > 0) {
                    allExpandableItems.push(item);
                    collectExpandableItems(item.children);
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
                        Expanded: {expandedItems.map((item) => item.name).join(", ") || "None"}
                    </Typography>
                </Box>
            </Box>
        </ThemeProvider>
    );
};

export const ControlledTreeExample = ({
    onSelectionChange = () => {},
    onExpansionChange = () => {},
}: {
    onSelectionChange?: (items: TreeItemData[]) => void;
    onExpansionChange?: (items: TreeItemData[]) => void;
}) => {
    const [selectedItem, setSelectedItem] = useState<TreeItemData | null>(null);
    const [expandedItems, setExpandedItems] = useState<TreeItemData[]>([]);

    const handleSelectionChange = (_event: React.SyntheticEvent, item: TreeItemData | null) => {
        setSelectedItem(item);
        onSelectionChange(item ? [item] : []);
    };

    const handleExpansionChange = (_event: React.SyntheticEvent, items: TreeItemData[]) => {
        setExpandedItems(items);
        onExpansionChange(items);
    };

    const expandAll = () => {
        const allExpandableItems: TreeItemData[] = [];
        const collectExpandableItems = (items: TreeItemData[]) => {
            items.forEach((item) => {
                if (item.children && item.children.length > 0) {
                    allExpandableItems.push(item);
                    collectExpandableItems(item.children);
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
                    <Typography variant="body2">Selected: {selectedItem?.name || "None"}</Typography>
                    <Typography variant="body2">
                        Expanded: {expandedItems.map((item) => item.name).join(", ") || "None"}
                    </Typography>
                </Box>
            </Box>
        </ThemeProvider>
    );
};
