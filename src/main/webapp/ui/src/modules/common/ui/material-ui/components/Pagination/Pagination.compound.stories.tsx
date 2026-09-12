import Pagination from "@mui/material/Pagination";
import PaginationItem from "@mui/material/PaginationItem";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { expect, userEvent, within } from "storybook/test";

const meta = {
  title: "Material UI/Navigation/Pagination/Composition",
  component: Pagination,
  parameters: { a11y: { test: "error" } },
  argTypes: {
    count: { control: { type: "number", min: 2, max: 10 } },
    shape: { control: "select", options: ["rounded", "circular"] },
    size: { control: "select", options: ["small", "medium", "large"] },
  },
} satisfies Meta<typeof Pagination>;
export default meta;
type Story = StoryObj<typeof meta>;

/** Forward renderItem props to PaginationItem to preserve labels, selection, and navigation. */
export const Composed: Story = {
  args: { count: 5, shape: "rounded", size: "medium" },
  render: function Pages(args) {
    const [page, setPage] = useState(1);
    return (
      <Pagination
        {...args}
        page={Math.min(page, args.count ?? 1)}
        onChange={(_, next) => setPage(next)}
        renderItem={(item) => <PaginationItem {...item} />}
      />
    );
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole("button", { name: "Go to page 2" }));
    await expect(canvas.getByRole("button", { name: "page 2" })).toHaveAttribute("aria-current", "page");
  },
};
