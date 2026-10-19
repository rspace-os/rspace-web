import ImageList from "@mui/material/ImageList";
import ImageListItem from "@mui/material/ImageListItem";
import ImageListItemBar from "@mui/material/ImageListItemBar";
import type { Meta, StoryObj } from "@storybook/react-vite";
import type { ComponentProps } from "react";

const meta = {
  title: "Material UI/Data Display/ImageList/Composition",
  component: ImageList,
  parameters: { a11y: { test: "error" } },
  argTypes: {
    cols: { control: { type: "number", min: 1, max: 3 } },
    gap: { control: { type: "number", min: 0, max: 24 } },
  },
} satisfies Meta<typeof ImageList>;
export default meta;
type Story = StoryObj<Omit<ComponentProps<typeof ImageList>, "children">>;

/** ImageList supplies the list and grid; each item pairs an image with its caption. */
export const Composed: Story = {
  args: { cols: 2, gap: 8 },
  render: (args) => (
    <ImageList {...args} aria-label="Sample images" sx={{ width: 360 }}>
      {["Sample A", "Sample B"].map((title) => (
        <ImageListItem key={title}>
          <img
            src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='180' height='100'%3E%3Crect width='180' height='100' fill='%23ddd'/%3E%3C/svg%3E"
            alt=""
          />
          <ImageListItemBar title={title} position="below" />
        </ImageListItem>
      ))}
    </ImageList>
  ),
};
