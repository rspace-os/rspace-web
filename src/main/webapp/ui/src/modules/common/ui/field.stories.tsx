import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { Input } from "./input";
import {
  Field,
  FieldContent,
  FieldDescription,
  FieldError,
  FieldGroup,
  FieldLabel,
  FieldLegend,
  FieldSeparator,
  FieldSet,
  FieldTitle,
} from "./field";

const meta = {
  title: "DesignSystem/Field",
  component: Field,
  tags: ["autodocs"],
} satisfies Meta<typeof Field>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => (
    <div style={{ width: "320px" }}>
      <Field>
        <FieldLabel htmlFor="field-story-email">Email</FieldLabel>
        <Input id="field-story-email" type="email" placeholder="you@example.com" />
        <FieldDescription>We&apos;ll never share your email.</FieldDescription>
      </Field>
    </div>
  ),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const input = canvas.getByPlaceholderText("you@example.com");
    expect(input).toBeInTheDocument();
  },
};

export const Orientations: Story = {
  render: () => (
    <div style={{ width: "420px", display: "flex", flexDirection: "column", gap: "24px" }}>
      <Field orientation="vertical">
        <FieldLabel htmlFor="field-story-vertical">Vertical</FieldLabel>
        <Input id="field-story-vertical" placeholder="Label above the control" />
      </Field>
      <Field orientation="horizontal">
        <FieldLabel htmlFor="field-story-horizontal">Horizontal</FieldLabel>
        <Input id="field-story-horizontal" placeholder="Label beside the control" />
      </Field>
      <Field orientation="responsive">
        <FieldLabel htmlFor="field-story-responsive">Responsive</FieldLabel>
        <Input id="field-story-responsive" placeholder="Stacks on small screens" />
      </Field>
    </div>
  ),
};

export const WithErrorAndFieldSet: Story = {
  render: () => (
    <div style={{ width: "360px" }}>
      <FieldSet>
        <FieldLegend>Account</FieldLegend>
        <FieldGroup>
          <Field data-invalid="true">
            <FieldLabel htmlFor="field-story-username">Username</FieldLabel>
            <Input id="field-story-username" aria-invalid defaultValue="a" />
            <FieldError errors={[{ message: "Username must be at least 3 characters." }]} />
          </Field>
          <FieldSeparator>or</FieldSeparator>
          <Field>
            <FieldContent>
              <FieldTitle>Two-factor authentication</FieldTitle>
              <FieldDescription>
                Require a code from your authenticator app when signing in.
              </FieldDescription>
            </FieldContent>
          </Field>
        </FieldGroup>
      </FieldSet>
    </div>
  ),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const error = await canvas.findByRole("alert");
    expect(error).toHaveTextContent("Username must be at least 3 characters.");
  },
};
