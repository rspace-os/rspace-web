import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { useId, useState } from "react";
import { expect, userEvent, within } from "storybook/test";
import { FieldError } from "./field";
import { Input } from "./input";
import { Label } from "./label";

const meta = {
  title: "DesignSystem/Input",
  component: Input,
  tags: ["autodocs"],
} satisfies Meta<typeof Input>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    placeholder: "Email",
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const input = canvas.getByPlaceholderText("Email");
    await userEvent.type(input, "hello@example.com");
    expect(input).toHaveValue("hello@example.com");
  },
};

export const Disabled: Story = {
  args: {
    placeholder: "Disabled input",
    disabled: true,
  },
};

export const File: Story = {
  args: {
    type: "file",
    "aria-label": "Upload file",
  },
};

/** A booking-style time field. A half-typed time warns on blur, since WebKit's grey filler can make it look complete. */
function TimeField({
  initialValue = "",
  error,
  disabled,
}: {
  initialValue?: string;
  error?: string;
  disabled?: boolean;
}) {
  const id = useId();
  const [value, setValue] = useState(initialValue);
  const [incomplete, setIncomplete] = useState(false);
  const message = incomplete ? "Finish entering the time: hours and minutes." : error;
  return (
    <div className="max-w-xs space-y-2">
      <Label htmlFor={id}>{"Start time"}</Label>
      <Input
        id={id}
        type="time"
        required
        disabled={disabled}
        aria-invalid={message ? true : undefined}
        aria-describedby={message ? `${id}-error` : undefined}
        value={value}
        onChange={(event) => {
          setValue(event.currentTarget.value);
          if (event.currentTarget.value) setIncomplete(false);
        }}
        onBlur={(event) => setIncomplete(event.currentTarget.validity.badInput)}
      />
      {message ? <FieldError id={`${id}-error`}>{message}</FieldError> : null}
    </div>
  );
}

/** Empty time fields hide WebKit's stand-in current time until focused; other engines keep their `--:--` hint. */
export const TimeEmpty: Story = {
  render: () => <TimeField />,
};

export const TimeFilled: Story = {
  render: () => <TimeField initialValue="09:30" />,
};

export const TimeRequiredError: Story = {
  render: () => <TimeField error="Enter a start time." />,
};

export const TimeDisabled: Story = {
  render: () => <TimeField disabled />,
};

/** Opening hours label each boundary only through `aria-label`. */
export const TimeRange: Story = {
  render: function TimeRange() {
    const [range, setRange] = useState({ start: "", end: "" });
    return (
      <div className="grid max-w-xs grid-cols-[minmax(5.5rem,1fr)_auto_minmax(5.5rem,1fr)] items-center gap-2">
        <Input
          aria-label="Opening time"
          type="time"
          required
          value={range.start}
          onChange={(event) => setRange({ ...range, start: event.currentTarget.value })}
        />
        <span aria-hidden="true" className="text-muted-foreground">
          {"–"}
        </span>
        <Input
          aria-label="Closing time"
          type="time"
          required
          value={range.end}
          onChange={(event) => setRange({ ...range, end: event.currentTarget.value })}
        />
      </div>
    );
  },
};
