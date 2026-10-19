/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Breadcrumbs/Breadcrumbs.stories.tsx
 * Upstream project license: MIT
 */
import GrainIcon from "@mui/icons-material/Grain";
import HomeIcon from "@mui/icons-material/Home";
import NavigateNextIcon from "@mui/icons-material/NavigateNext";
import WhatshotIcon from "@mui/icons-material/Whatshot";
import Breadcrumbs from "@mui/material/Breadcrumbs";
import Link from "@mui/material/Link";
import Typography from "@mui/material/Typography";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, userEvent, within } from "storybook/test";
import { createNumberArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Navigation/Breadcrumbs",
  component: Breadcrumbs,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    maxItems: createNumberArgType("Specifies the maximum number of breadcrumbs to display.", 8, 1, 20, "Content"),
    itemsAfterCollapse: createNumberArgType(
      "If max items is exceeded, the number of items to show after the ellipsis.",
      1,
      0,
      5,
      "Content",
    ),
    itemsBeforeCollapse: createNumberArgType(
      "If max items is exceeded, the number of items to show before the ellipsis.",
      1,
      0,
      5,
      "Content",
    ),
    separator: {
      control: "text",
      description: "Custom separator node.",
      table: {
        category: "Appearance",
        defaultValue: { summary: "/" },
      },
    },
    children: { control: false },
  },
} satisfies Meta<typeof Breadcrumbs>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: {
    maxItems: 8,
    itemsAfterCollapse: 1,
    itemsBeforeCollapse: 1,
  },
  render: (args) => (
    <Breadcrumbs {...args} aria-label="breadcrumb">
      <Link underline="hover" color="inherit" href="/">
        Home
      </Link>
      <Link underline="hover" color="inherit" href="/products">
        Products
      </Link>
      <Typography color="text.primary">Details</Typography>
    </Breadcrumbs>
  ),
};

export const Default: Story = {
  render: () => (
    <Breadcrumbs aria-label="breadcrumb">
      <Link underline="hover" color="inherit" href="/">
        Home
      </Link>
      <Link underline="hover" color="inherit" href="/products">
        Products
      </Link>
      <Typography color="text.primary">Details</Typography>
    </Breadcrumbs>
  ),
};

export function WithIcons() {
  return (
    <Breadcrumbs aria-label="breadcrumb">
      <Link underline="hover" sx={{ display: "flex", alignItems: "center" }} color="inherit" href="/">
        <HomeIcon sx={{ mr: 0.5 }} fontSize="inherit" />
        Home
      </Link>
      <Link underline="hover" sx={{ display: "flex", alignItems: "center" }} color="inherit" href="/">
        <WhatshotIcon sx={{ mr: 0.5 }} fontSize="inherit" />
        Trending
      </Link>
      <Typography sx={{ display: "flex", alignItems: "center" }} color="text.primary">
        <GrainIcon sx={{ mr: 0.5 }} fontSize="inherit" />
        Details
      </Typography>
    </Breadcrumbs>
  );
}

export function CustomSeparator() {
  return (
    <Breadcrumbs separator={<NavigateNextIcon fontSize="small" />} aria-label="breadcrumb">
      <Link underline="hover" color="inherit" href="/">
        Home
      </Link>
      <Link underline="hover" color="inherit" href="/products">
        Products
      </Link>
      <Typography color="text.primary">Details</Typography>
    </Breadcrumbs>
  );
}

export function Collapsed() {
  return (
    <Breadcrumbs maxItems={3} aria-label="breadcrumb">
      <Link underline="hover" color="inherit" href="/">
        Home
      </Link>
      <Link underline="hover" color="inherit" href="/category">
        Category
      </Link>
      <Link underline="hover" color="inherit" href="/subcategory">
        Subcategory
      </Link>
      <Link underline="hover" color="inherit" href="/product">
        Product
      </Link>
      <Typography color="text.primary">Details</Typography>
    </Breadcrumbs>
  );
}

export const InteractionTest: Story = {
  render: () => (
    <Breadcrumbs aria-label="test breadcrumb">
      <Link underline="hover" color="inherit" href="/">
        Home
      </Link>
      <Link underline="hover" color="inherit" href="/products">
        Products
      </Link>
      <Typography color="text.primary">Details</Typography>
    </Breadcrumbs>
  ),
  play: async ({ canvasElement }) => {
    const navigation = within(within(canvasElement).getByRole("navigation", { name: "test breadcrumb" }));
    await expect(navigation.getByRole("link", { name: "Home" })).toHaveAttribute("href", "/");
    await expect(navigation.getByRole("link", { name: "Products" })).toHaveAttribute("href", "/products");
    await expect(navigation.getByText("Details")).toBeVisible();
  },
};

export const ExpandCollapsed: Story = {
  render: () => <Collapsed />,
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await expect(canvas.queryByRole("link", { name: "Subcategory" })).not.toBeInTheDocument();
    await userEvent.click(canvas.getByRole("button", { name: "Show path" }));
    await expect(canvas.getByRole("link", { name: "Subcategory" })).toBeVisible();
  },
};
