/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Link/Link.stories.tsx
 * Upstream project license: MIT
 */
import Link from "@mui/material/Link";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, within } from "storybook/test";
import { createSelectArgType, muiColorArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Navigation/Link",
  component: Link,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    color: muiColorArgType,
    underline: createSelectArgType(
      ["always", "hover", "none"],
      "always",
      "Controls when the link should have an underline.",
      "Appearance",
    ),
    variant: createSelectArgType(
      [
        "body1",
        "body2",
        "button",
        "caption",
        "h1",
        "h2",
        "h3",
        "h4",
        "h5",
        "h6",
        "inherit",
        "overline",
        "subtitle1",
        "subtitle2",
      ],
      "inherit",
      "Applies the theme typography styles.",
      "Appearance",
    ),
    href: {
      control: "text",
      description: "The URL to link to.",
      table: { category: "Content" },
    },
    children: { control: false },
  },
} satisfies Meta<typeof Link>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: {
    href: "https://mui.com",
    children: "Playground Link",
    color: "primary",
    underline: "always",
  },
};

export const Default: Story = {
  args: {
    href: "https://mui.com",
    children: "Default Link",
  },
};

export function UnderlineVariants() {
  return (
    <Stack spacing={2}>
      <Link href="https://mui.com" underline="always">
        underline always
      </Link>
      <Link href="https://mui.com" underline="hover">
        underline hover
      </Link>
      <Link href="https://mui.com" underline="none">
        underline none
      </Link>
    </Stack>
  );
}

export function Colors() {
  return (
    <Stack spacing={2}>
      <Link href="https://mui.com" color="primary">
        Primary
      </Link>
      <Link href="https://mui.com" color="secondary">
        Secondary
      </Link>
      <Link href="https://mui.com" color="inherit">
        Inherit
      </Link>
      <Link href="https://mui.com" color="error">
        Error
      </Link>
    </Stack>
  );
}

export function ButtonBehavior() {
  return (
    <Link component="button" variant="body2" onClick={fn()}>
      Button Link
    </Link>
  );
}

export function WithinText() {
  return (
    <Typography>
      This is some text with a <Link href="https://mui.com">link</Link> inside it.
    </Typography>
  );
}

export const InteractionTest: Story = {
  args: {
    href: "#test",
    children: "Test Link",
    onClick: fn((e: React.MouseEvent) => e.preventDefault()),
  },
  play: async ({ args, canvasElement, step }) => {
    const canvas = within(canvasElement);

    await step("Verify link renders", async () => {
      const link = canvas.getByRole("link", { name: "Test Link" });
      await expect(link).toBeInTheDocument();
    });

    await step("Verify href attribute", async () => {
      const link = canvas.getByRole("link", { name: "Test Link" });
      await expect(link).toHaveAttribute("href", "#test");
    });

    await step("Test link click interaction", async () => {
      const link = canvas.getByRole("link", { name: "Test Link" });
      await userEvent.click(link);
      await expect(args.onClick).toHaveBeenCalled();
    });

    await step("Test accessibility", async () => {
      const link = canvas.getByRole("link", { name: "Test Link" });
      await expect(link.tagName).toBe("A");
    });
  },
};
