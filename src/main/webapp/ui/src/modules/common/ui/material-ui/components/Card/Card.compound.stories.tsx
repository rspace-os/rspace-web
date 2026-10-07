import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActionArea from "@mui/material/CardActionArea";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import CardHeader from "@mui/material/CardHeader";
import CardMedia from "@mui/material/CardMedia";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { expect, userEvent, within } from "storybook/test";

const meta = {
  title: "Material UI/Surfaces/Card/Composition",
  component: Card,
  parameters: { a11y: { test: "error" } },
  argTypes: { variant: { control: "select", options: ["outlined", "elevation"] } },
} satisfies Meta<typeof Card>;
export default meta;
type Story = StoryObj<typeof meta>;

/** Keep secondary actions outside CardActionArea so buttons are not nested. */
export const Composed: Story = {
  args: { variant: "outlined" },
  render: function SampleCard(args) {
    const [saved, setSaved] = useState(false);
    const [opened, setOpened] = useState(false);
    return (
      <>
        <Card {...args} sx={{ maxWidth: 360 }}>
          <CardHeader title="Sample A" subheader="Refrigerated storage" />
          <CardActionArea onClick={() => setOpened(true)} aria-label="Open sample A">
            <CardMedia
              component="img"
              height="100"
              image="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='360' height='100'%3E%3Crect width='360' height='100' fill='%23ddd'/%3E%3C/svg%3E"
              alt=""
            />
            <CardContent>View the sample and its storage details.</CardContent>
          </CardActionArea>
          <CardActions>
            <Button onClick={() => setSaved(!saved)}>{saved ? "Unsave" : "Save"}</Button>
          </CardActions>
        </Card>
        <p role="status">{opened ? "Sample A opened" : "Select a sample"}</p>
      </>
    );
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole("button", { name: "Open sample A" }));
    await expect(canvas.getByRole("status")).toHaveTextContent("Sample A opened");
    await userEvent.click(canvas.getByRole("button", { name: "Save" }));
    await expect(canvas.getByRole("button", { name: "Unsave" })).toBeVisible();
  },
};
