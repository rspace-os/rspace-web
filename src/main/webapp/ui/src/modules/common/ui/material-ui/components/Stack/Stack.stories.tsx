/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Stack/Stack.stories.tsx
 * Upstream project license: MIT
 */
import Divider from "@mui/material/Divider";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import { styled } from "@mui/material/styles";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { createNumberArgType, createSelectArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Layout/Stack",
  component: Stack,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    direction: createSelectArgType(
      ["row", "row-reverse", "column", "column-reverse"],
      "column",
      "Defines the flex-direction style property.",
      "Layout",
    ),
    spacing: createNumberArgType("Defines the space between children.", 2, 0, 10, "Layout"),
    // Disable divider and children as they require JSX
    divider: { control: false },
    children: { control: false },
  },
} satisfies Meta<typeof Stack>;

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
    direction: "row",
    spacing: 2,
  },
  render: (args) => (
    <Stack {...args}>
      <Item>Item 1</Item>
      <Item>Item 2</Item>
      <Item>Item 3</Item>
    </Stack>
  ),
};

export const Default: Story = {
  render: () => (
    <Stack spacing={2}>
      <Item>Item 1</Item>
      <Item>Item 2</Item>
      <Item>Item 3</Item>
    </Stack>
  ),
};

export function Horizontal() {
  return (
    <Stack direction="row" spacing={2}>
      <Item>Item 1</Item>
      <Item>Item 2</Item>
      <Item>Item 3</Item>
    </Stack>
  );
}

export function DifferentSpacing() {
  return (
    <Stack spacing={4}>
      <div>
        <div style={{ marginBottom: 8 }}>spacing=1</div>
        <Stack direction="row" spacing={1}>
          <Item>Item 1</Item>
          <Item>Item 2</Item>
          <Item>Item 3</Item>
        </Stack>
      </div>
      <div>
        <div style={{ marginBottom: 8 }}>spacing=2</div>
        <Stack direction="row" spacing={2}>
          <Item>Item 1</Item>
          <Item>Item 2</Item>
          <Item>Item 3</Item>
        </Stack>
      </div>
      <div>
        <div style={{ marginBottom: 8 }}>spacing=4</div>
        <Stack direction="row" spacing={4}>
          <Item>Item 1</Item>
          <Item>Item 2</Item>
          <Item>Item 3</Item>
        </Stack>
      </div>
    </Stack>
  );
}

export function WithDividers() {
  return (
    <Stack direction="column" divider={<Divider orientation="horizontal" flexItem />} spacing={2}>
      <Item>Item 1</Item>
      <Item>Item 2</Item>
      <Item>Item 3</Item>
    </Stack>
  );
}

export function ResponsiveDirection() {
  return (
    <Stack direction={{ xs: "column", sm: "row" }} spacing={{ xs: 1, sm: 2, md: 4 }}>
      <Item>Item 1</Item>
      <Item>Item 2</Item>
      <Item>Item 3</Item>
    </Stack>
  );
}

export function WithAlignItems() {
  return (
    <Stack spacing={4}>
      <div>
        <div style={{ marginBottom: 8 }}>alignItems=center</div>
        <Stack
          direction="row"
          spacing={2}
          sx={{ height: 100, backgroundColor: "grey.100", p: 1, alignItems: "center" }}
        >
          <Item sx={{ height: 40 }}>Short</Item>
          <Item sx={{ height: 60 }}>Medium</Item>
          <Item sx={{ height: 80 }}>Tall</Item>
        </Stack>
      </div>
    </Stack>
  );
}

export function WithJustifyContent() {
  return (
    <Stack spacing={4}>
      <div>
        <div style={{ marginBottom: 8 }}>justifyContent=space-between</div>
        <Stack direction="row" spacing={2} sx={{ backgroundColor: "grey.100", p: 1, justifyContent: "space-between" }}>
          <Item>Item 1</Item>
          <Item>Item 2</Item>
          <Item>Item 3</Item>
        </Stack>
      </div>
    </Stack>
  );
}

export const InteractionTest: Story = {
  render: () => (
    <Stack spacing={2} data-testid="stack-container">
      <Item data-testid="stack-item-1">Item 1</Item>
      <Item data-testid="stack-item-2">Item 2</Item>
      <Item data-testid="stack-item-3">Item 3</Item>
    </Stack>
  ),
  play: async ({ canvasElement, step }) => {
    const canvas = within(canvasElement);

    await step("Verify Stack container renders", async () => {
      const container = canvas.getByTestId("stack-container");
      expect(container).toBeInTheDocument();
    });

    await step("Verify all Stack items render", async () => {
      const item1 = canvas.getByTestId("stack-item-1");
      const item2 = canvas.getByTestId("stack-item-2");
      const item3 = canvas.getByTestId("stack-item-3");

      expect(item1).toBeInTheDocument();
      expect(item2).toBeInTheDocument();
      expect(item3).toBeInTheDocument();

      expect(item1).toHaveTextContent("Item 1");
      expect(item2).toHaveTextContent("Item 2");
      expect(item3).toHaveTextContent("Item 3");
    });
  },
};
