/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Tabs/Tabs.stories.tsx
 * Upstream project license: MIT
 */

import FavoriteIcon from "@mui/icons-material/Favorite";
import PersonPinIcon from "@mui/icons-material/PersonPin";
import PhoneIcon from "@mui/icons-material/Phone";
import Box from "@mui/material/Box";
import Tab from "@mui/material/Tab";
import Tabs from "@mui/material/Tabs";
import Typography from "@mui/material/Typography";
import type { Meta, StoryObj } from "@storybook/react-vite";
import React from "react";
import { useArgs } from "storybook/preview-api";
import { expect, fn, userEvent, within } from "storybook/test";
import { createBooleanArgType, createSelectArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Navigation/Tabs",
  component: Tabs,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    value: {
      control: { type: "number", min: 0, max: 2 },
      description: "The value of the currently selected Tab.",
      table: { category: "State" },
    },
    orientation: createSelectArgType(["horizontal", "vertical"], "horizontal", "The component orientation.", "Layout"),
    variant: createSelectArgType(
      ["standard", "scrollable", "fullWidth"],
      "standard",
      "Determines additional display behavior of the tabs.",
      "Appearance",
    ),
    centered: createBooleanArgType("If true, the tabs are centered.", false, "Layout"),
    textColor: createSelectArgType(
      ["inherit", "primary", "secondary"],
      "inherit",
      "Determines the color of the Tab.",
      "Appearance",
    ),
    indicatorColor: createSelectArgType(
      ["primary", "secondary"],
      "primary",
      "Determines the color of the indicator.",
      "Appearance",
    ),
    children: { control: false },
  },
} satisfies Meta<typeof Tabs>;

export default meta;
type Story = StoryObj<typeof meta>;

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;
  return (
    <div role="tabpanel" id={`panel-${index}`} aria-labelledby={`tab-${index}`} hidden={value !== index} {...other}>
      {value === index && (
        <Box sx={{ p: 3 }}>
          <Typography>{children}</Typography>
        </Box>
      )}
    </div>
  );
}

export const Playground: Story = {
  args: {
    value: 0,
    orientation: "horizontal",
    variant: "standard",
    centered: false,
    textColor: "primary",
    indicatorColor: "primary",
  },
  render: function Playground(args) {
    const [, updateArgs] = useArgs();
    return (
      <Box sx={{ width: "100%" }}>
        <Box sx={{ borderBottom: 1, borderColor: "divider" }}>
          <Tabs {...args} aria-label="Example tabs" onChange={(_, value) => updateArgs({ value })}>
            <Tab label="Item One" />
            <Tab label="Item Two" />
            <Tab label="Item Three" />
          </Tabs>
        </Box>
      </Box>
    );
  },
};

export const Default: Story = {
  render: () => {
    const [value, setValue] = React.useState(0);
    return (
      <Box sx={{ width: "100%" }}>
        <Box sx={{ borderBottom: 1, borderColor: "divider" }}>
          <Tabs value={value} onChange={(_, newValue) => setValue(newValue)}>
            <Tab label="Item One" id="tab-0" aria-controls="panel-0" />
            <Tab label="Item Two" id="tab-1" aria-controls="panel-1" />
            <Tab label="Item Three" id="tab-2" aria-controls="panel-2" />
          </Tabs>
        </Box>
        <TabPanel value={value} index={0}>
          Item One Content
        </TabPanel>
        <TabPanel value={value} index={1}>
          Item Two Content
        </TabPanel>
        <TabPanel value={value} index={2}>
          Item Three Content
        </TabPanel>
      </Box>
    );
  },
};

export function Basic() {
  const [value, setValue] = React.useState(0);
  return (
    <Box sx={{ width: "100%" }}>
      <Box sx={{ borderBottom: 1, borderColor: "divider" }}>
        <Tabs value={value} onChange={(_, newValue) => setValue(newValue)}>
          <Tab label="Item One" id="tab-0" aria-controls="panel-0" />
          <Tab label="Item Two" id="tab-1" aria-controls="panel-1" />
          <Tab label="Item Three" id="tab-2" aria-controls="panel-2" />
        </Tabs>
      </Box>
      <TabPanel value={value} index={0}>
        Content for Tab One
      </TabPanel>
      <TabPanel value={value} index={1}>
        Content for Tab Two
      </TabPanel>
      <TabPanel value={value} index={2}>
        Content for Tab Three
      </TabPanel>
    </Box>
  );
}

export function Centered() {
  const [value, setValue] = React.useState(0);
  return (
    <Box sx={{ width: "100%" }}>
      <Tabs value={value} onChange={(_, newValue) => setValue(newValue)} centered>
        <Tab label="Item One" />
        <Tab label="Item Two" />
        <Tab label="Item Three" />
      </Tabs>
    </Box>
  );
}

export function Scrollable() {
  const [value, setValue] = React.useState(0);
  return (
    <Box sx={{ maxWidth: 480 }}>
      <Tabs value={value} onChange={(_, newValue) => setValue(newValue)} variant="scrollable" scrollButtons="auto">
        <Tab label="Item One" />
        <Tab label="Item Two" />
        <Tab label="Item Three" />
        <Tab label="Item Four" />
        <Tab label="Item Five" />
        <Tab label="Item Six" />
        <Tab label="Item Seven" />
      </Tabs>
    </Box>
  );
}

export function IconTabs() {
  const [value, setValue] = React.useState(0);
  return (
    <Tabs value={value} onChange={(_, newValue) => setValue(newValue)}>
      <Tab icon={<PhoneIcon />} aria-label="phone" />
      <Tab icon={<FavoriteIcon />} aria-label="favorite" />
      <Tab icon={<PersonPinIcon />} aria-label="person" />
    </Tabs>
  );
}

export function IconLabelTabs() {
  const [value, setValue] = React.useState(0);
  return (
    <Tabs value={value} onChange={(_, newValue) => setValue(newValue)}>
      <Tab icon={<PhoneIcon />} label="RECENTS" />
      <Tab icon={<FavoriteIcon />} label="FAVORITES" />
      <Tab icon={<PersonPinIcon />} label="NEARBY" />
    </Tabs>
  );
}

export function Vertical() {
  const [value, setValue] = React.useState(0);
  return (
    <Box
      sx={{
        flexGrow: 1,
        bgcolor: "background.paper",
        display: "flex",
        height: 224,
      }}
    >
      <Tabs
        orientation="vertical"
        variant="scrollable"
        value={value}
        onChange={(_, newValue) => setValue(newValue)}
        sx={{ borderRight: 1, borderColor: "divider" }}
      >
        <Tab label="Item One" id="tab-0" aria-controls="panel-0" />
        <Tab label="Item Two" id="tab-1" aria-controls="panel-1" />
        <Tab label="Item Three" id="tab-2" aria-controls="panel-2" />
        <Tab label="Item Four" id="tab-3" aria-controls="panel-3" />
      </Tabs>
      <TabPanel value={value} index={0}>
        Item One
      </TabPanel>
      <TabPanel value={value} index={1}>
        Item Two
      </TabPanel>
      <TabPanel value={value} index={2}>
        Item Three
      </TabPanel>
      <TabPanel value={value} index={3}>
        Item Four
      </TabPanel>
    </Box>
  );
}

export function Colors() {
  const [value, setValue] = React.useState(0);
  return (
    <Box sx={{ width: "100%" }}>
      <Box sx={{ mb: 2 }}>
        <Tabs value={value} onChange={(_, newValue) => setValue(newValue)} textColor="primary" indicatorColor="primary">
          <Tab label="Primary" />
          <Tab label="Primary" />
        </Tabs>
      </Box>
      <Box>
        <Tabs
          value={value}
          onChange={(_, newValue) => setValue(newValue)}
          textColor="secondary"
          indicatorColor="secondary"
        >
          <Tab label="Secondary" />
          <Tab label="Secondary" />
        </Tabs>
      </Box>
    </Box>
  );
}

export const InteractionTest: Story = {
  render: () => {
    const [value, setValue] = React.useState(0);
    const handleChange = fn((_, newValue: number) => setValue(newValue));

    return (
      <Box sx={{ width: "100%" }} data-testid="tabs-container">
        <Box sx={{ borderBottom: 1, borderColor: "divider" }}>
          <Tabs value={value} onChange={handleChange} aria-label="test tabs">
            <Tab label="Item One" id="tab-0" aria-controls="panel-0" />
            <Tab label="Item Two" id="tab-1" aria-controls="panel-1" />
            <Tab label="Item Three" id="tab-2" aria-controls="panel-2" />
          </Tabs>
        </Box>
        <TabPanel value={value} index={0}>
          Content for Tab One
        </TabPanel>
        <TabPanel value={value} index={1}>
          Content for Tab Two
        </TabPanel>
        <TabPanel value={value} index={2}>
          Content for Tab Three
        </TabPanel>
      </Box>
    );
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);

    // Verify initial render with all tabs present
    const tab1 = canvas.getByRole("tab", { name: /item one/i });
    const tab2 = canvas.getByRole("tab", { name: /item two/i });
    const tab3 = canvas.getByRole("tab", { name: /item three/i });

    await expect(tab1).toBeInTheDocument();
    await expect(tab2).toBeInTheDocument();
    await expect(tab3).toBeInTheDocument();
    await expect(tab1).toHaveAttribute("aria-selected", "true");
    await userEvent.click(tab2);
    await expect(tab2).toHaveAttribute("aria-selected", "true");
    await expect(canvas.getByRole("tabpanel", { name: "Item Two" })).toHaveTextContent("Content for Tab Two");
    await userEvent.keyboard("{ArrowRight}{Enter}");
    await expect(tab3).toHaveAttribute("aria-selected", "true");
    await expect(canvas.getByRole("tabpanel", { name: "Item Three" })).toBeVisible();
  },
};
