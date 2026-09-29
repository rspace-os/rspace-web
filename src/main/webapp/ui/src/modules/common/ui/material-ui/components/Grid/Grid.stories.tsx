/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Grid/Grid.stories.tsx
 * Upstream project license: MIT
 */
import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";
import Paper from "@mui/material/Paper";
import { styled } from "@mui/material/styles";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { createNumberArgType, createSelectArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Layout/Grid",
  component: Grid,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    container: {
      control: "boolean",
      description: "If true, the component will have the flex container behavior.",
      table: {
        category: "Layout",
        defaultValue: { summary: "false" },
      },
    },
    spacing: createNumberArgType("Defines the space between children.", 2, 0, 10, "Layout"),
    direction: createSelectArgType(
      ["row", "row-reverse", "column", "column-reverse"],
      "row",
      "Defines the flex-direction style property.",
      "Layout",
    ),
    wrap: createSelectArgType(
      ["wrap", "nowrap", "wrap-reverse"],
      "wrap",
      "Defines the flex-wrap style property.",
      "Layout",
    ),
    children: { control: false },
  },
} satisfies Meta<typeof Grid>;

export default meta;
type Story = StoryObj<typeof meta>;

const Item = styled(Paper)(({ theme }) => ({
  backgroundColor: theme.palette.mode === "dark" ? "#1A2027" : "#fff",
  ...theme.typography.body2,
  padding: theme.spacing(2),
  textAlign: "center",
  color: theme.palette.text.secondary,
}));

export const Playground: Story = {
  args: {
    container: true,
    spacing: 2,
    direction: "row",
    wrap: "wrap",
  },
  render: (args) => (
    <Grid {...args}>
      <Grid size={4}>
        <Item>Item 1</Item>
      </Grid>
      <Grid size={4}>
        <Item>Item 2</Item>
      </Grid>
      <Grid size={4}>
        <Item>Item 3</Item>
      </Grid>
    </Grid>
  ),
};

export const Default: Story = {
  render: () => (
    <Grid container spacing={2}>
      <Grid size={12}>
        <Item>size=12</Item>
      </Grid>
      <Grid size={6}>
        <Item>size=6</Item>
      </Grid>
      <Grid size={6}>
        <Item>size=6</Item>
      </Grid>
    </Grid>
  ),
};

export function BasicGrid() {
  return (
    <Box sx={{ flexGrow: 1 }}>
      <Grid container spacing={2}>
        <Grid size={12}>
          <Item>size=12</Item>
        </Grid>
        <Grid size={6}>
          <Item>size=6</Item>
        </Grid>
        <Grid size={6}>
          <Item>size=6</Item>
        </Grid>
        <Grid size={4}>
          <Item>size=4</Item>
        </Grid>
        <Grid size={4}>
          <Item>size=4</Item>
        </Grid>
        <Grid size={4}>
          <Item>size=4</Item>
        </Grid>
      </Grid>
    </Box>
  );
}

export function ResponsiveGrid() {
  return (
    <Box sx={{ flexGrow: 1 }}>
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
          <Item>xs=12 sm=6 md=4 lg=3</Item>
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
          <Item>xs=12 sm=6 md=4 lg=3</Item>
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
          <Item>xs=12 sm=6 md=4 lg=3</Item>
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
          <Item>xs=12 sm=6 md=4 lg=3</Item>
        </Grid>
      </Grid>
    </Box>
  );
}

export function GridSpacing() {
  return (
    <Box sx={{ flexGrow: 1 }}>
      <Box sx={{ mb: 4 }}>
        <Box sx={{ mb: 1 }}>spacing=1</Box>
        <Grid container spacing={1}>
          <Grid size={4}>
            <Item>Item</Item>
          </Grid>
          <Grid size={4}>
            <Item>Item</Item>
          </Grid>
          <Grid size={4}>
            <Item>Item</Item>
          </Grid>
        </Grid>
      </Box>
      <Box sx={{ mb: 4 }}>
        <Box sx={{ mb: 1 }}>spacing=2</Box>
        <Grid container spacing={2}>
          <Grid size={4}>
            <Item>Item</Item>
          </Grid>
          <Grid size={4}>
            <Item>Item</Item>
          </Grid>
          <Grid size={4}>
            <Item>Item</Item>
          </Grid>
        </Grid>
      </Box>
    </Box>
  );
}

export function NestedGrid() {
  return (
    <Box sx={{ flexGrow: 1 }}>
      <Grid container spacing={2}>
        <Grid size={12}>
          <Item>Header</Item>
        </Grid>
        <Grid size={3}>
          <Item>Sidebar</Item>
        </Grid>
        <Grid size={9}>
          <Grid container spacing={2}>
            <Grid size={6}>
              <Item>Content 1</Item>
            </Grid>
            <Grid size={6}>
              <Item>Content 2</Item>
            </Grid>
          </Grid>
        </Grid>
        <Grid size={12}>
          <Item>Footer</Item>
        </Grid>
      </Grid>
    </Box>
  );
}

export function AutoLayoutGrid() {
  return (
    <Box sx={{ flexGrow: 1 }}>
      <Grid container spacing={2}>
        <Grid size="grow">
          <Item>size=grow</Item>
        </Grid>
        <Grid size={6}>
          <Item>size=6</Item>
        </Grid>
        <Grid size="grow">
          <Item>size=grow</Item>
        </Grid>
      </Grid>
    </Box>
  );
}

export const InteractionTest: Story = {
  render: () => (
    <Box sx={{ flexGrow: 1 }} data-testid="grid-container">
      <Grid container spacing={2}>
        <Grid size={6}>
          <Item data-testid="grid-item-1">Item 1</Item>
        </Grid>
        <Grid size={6}>
          <Item data-testid="grid-item-2">Item 2</Item>
        </Grid>
        <Grid size={12}>
          <Item data-testid="grid-item-3">Item 3</Item>
        </Grid>
      </Grid>
    </Box>
  ),
  play: async ({ canvasElement, step }) => {
    const canvas = within(canvasElement);

    await step("Verify Grid container renders", async () => {
      const container = canvas.getByTestId("grid-container");
      expect(container).toBeInTheDocument();
    });

    await step("Verify all Grid items render", async () => {
      const item1 = canvas.getByTestId("grid-item-1");
      const item2 = canvas.getByTestId("grid-item-2");
      const item3 = canvas.getByTestId("grid-item-3");

      expect(item1).toBeInTheDocument();
      expect(item2).toBeInTheDocument();
      expect(item3).toBeInTheDocument();

      expect(item1).toHaveTextContent("Item 1");
      expect(item2).toHaveTextContent("Item 2");
      expect(item3).toHaveTextContent("Item 3");
      const first = item1.getBoundingClientRect();
      const second = item2.getBoundingClientRect();
      const third = item3.getBoundingClientRect();
      expect(first.width).toBeGreaterThan(0);
      expect(second.top).toBe(first.top);
      expect(third.top).toBeGreaterThan(first.top);
      expect(third.width).toBeGreaterThan(first.width);
    });
  },
};
