import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import type { Meta, StoryObj } from "@storybook/react-vite";

const meta = {
  title: "Material UI/Feedback/Alert/Composition",
  component: Alert,
  parameters: { a11y: { test: "error" } },
  argTypes: {
    severity: { control: "select", options: ["info", "success", "warning", "error"] },
    variant: { control: "select", options: ["standard", "filled", "outlined"] },
  },
} satisfies Meta<typeof Alert>;
export default meta;
type Story = StoryObj<typeof meta>;

/** AlertTitle belongs inside Alert and inherits its severity styling. */
export const Composed: Story = {
  args: { severity: "info", variant: "standard" },
  render: (args) => (
    <Alert {...args}>
      <AlertTitle>Storage notice</AlertTitle>Keep samples refrigerated.
    </Alert>
  ),
};
