import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { Button } from "./button";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardAction,
  CardContent,
  CardFooter,
} from "./card";

const meta = {
  title: "DesignSystem/Card",
  component: Card,
  tags: ["autodocs"],
} satisfies Meta<typeof Card>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => (
    <Card style={{ width: "360px" }}>
      <CardHeader>
        <CardTitle>Create project</CardTitle>
        <CardDescription>
          Deploy your new project in one click.
        </CardDescription>
        <CardAction>
          <Button variant="ghost" size="icon" aria-label="Settings">
            ...
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent>
        <p>Project details and configuration go here.</p>
      </CardContent>
      <CardFooter style={{ gap: "8px" }}>
        <Button variant="outline">Cancel</Button>
        <Button>Deploy</Button>
      </CardFooter>
    </Card>
  ),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByText("Create project")).toBeInTheDocument();
    expect(
      canvas.getByRole("button", { name: /deploy/i })
    ).toBeInTheDocument();
  },
};

export const AllSizes: Story = {
  render: () => (
    <div style={{ display: "flex", gap: "16px", flexWrap: "wrap" }}>
      <Card style={{ width: "320px" }}>
        <CardHeader>
          <CardTitle>Default size</CardTitle>
          <CardDescription>Standard card spacing.</CardDescription>
        </CardHeader>
        <CardContent>
          <p>Uses the default --card-spacing scale.</p>
        </CardContent>
      </Card>
      <Card size="sm" style={{ width: "320px" }}>
        <CardHeader>
          <CardTitle>Small size</CardTitle>
          <CardDescription>Compact card spacing.</CardDescription>
        </CardHeader>
        <CardContent>
          <p>Uses the sm --card-spacing scale.</p>
        </CardContent>
      </Card>
    </div>
  ),
};
