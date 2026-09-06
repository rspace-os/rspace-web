/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/ToggleButton/ToggleButton.stories.tsx
 * Upstream project license: MIT
 */
import FormatAlignCenterIcon from "@mui/icons-material/FormatAlignCenter";
import FormatAlignJustifyIcon from "@mui/icons-material/FormatAlignJustify";
import FormatAlignLeftIcon from "@mui/icons-material/FormatAlignLeft";
import FormatAlignRightIcon from "@mui/icons-material/FormatAlignRight";
import FormatBoldIcon from "@mui/icons-material/FormatBold";
import FormatItalicIcon from "@mui/icons-material/FormatItalic";
import FormatUnderlinedIcon from "@mui/icons-material/FormatUnderlined";
import ViewListIcon from "@mui/icons-material/ViewList";
import ViewModuleIcon from "@mui/icons-material/ViewModule";
import ViewQuiltIcon from "@mui/icons-material/ViewQuilt";
import Box from "@mui/material/Box";
import ToggleButton from "@mui/material/ToggleButton";
import ToggleButtonGroup from "@mui/material/ToggleButtonGroup";
import type { Meta, StoryObj } from "@storybook/react-vite";
import React from "react";
import { useArgs } from "storybook/preview-api";
import { expect, fn, userEvent, waitFor, within } from "storybook/test";
import {
  createBooleanArgType,
  muiColorArgType,
  muiDisabledArgType,
  muiSelectedArgType,
  muiSizeArgType,
} from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Inputs/ToggleButton",
  component: ToggleButton,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    color: muiColorArgType,
    size: muiSizeArgType,
    disabled: muiDisabledArgType,
    selected: muiSelectedArgType,
    disableRipple: createBooleanArgType("If true, the ripple effect is disabled.", false, "Appearance"),
    fullWidth: createBooleanArgType(
      "If true, the button will take up the full width of its container.",
      false,
      "Layout",
    ),
    value: {
      control: "text",
      description: "The value to associate with the button when selected in a ToggleButtonGroup.",
      table: { category: "Content" },
    },
    children: { control: false },
  },
} satisfies Meta<typeof ToggleButton>;

export default meta;
type Story = StoryObj<typeof meta>;

// Keep React state inside a component so standalone story tests also rerender after a click.
function PlaygroundToggleButton(props: React.ComponentProps<typeof ToggleButton>) {
  const [selected, setSelected] = React.useState(props.selected);
  React.useEffect(() => setSelected(props.selected), [props.selected]);
  return (
    <ToggleButton
      {...props}
      selected={selected}
      onChange={(event, value) => {
        setSelected(!selected);
        props.onChange?.(event, value);
      }}
    />
  );
}

export const Playground: Story = {
  args: {
    value: "bold",
    selected: false,
    color: "primary",
    size: "medium",
    disabled: false,
    children: <FormatBoldIcon />,
    "aria-label": "Bold",
  },
  render: function PlaygroundToggle(args) {
    const [, updateArgs] = useArgs();
    return <PlaygroundToggleButton {...args} onChange={() => updateArgs({ selected: !args.selected })} />;
  },
  play: async ({ canvasElement }) => {
    const button = within(canvasElement).getByRole("button", { name: "Bold" });
    await userEvent.click(button);
    await waitFor(() => expect(button).toHaveAttribute("aria-pressed", "true"));
    await userEvent.click(button);
    await waitFor(() => expect(button).toHaveAttribute("aria-pressed", "false"));
  },
};

export const Default: Story = { ...Playground };

export function SingleSelection() {
  const [alignment, setAlignment] = React.useState<string | null>("left");

  const handleAlignment = (_event: React.MouseEvent<HTMLElement>, newAlignment: string | null) => {
    setAlignment(newAlignment);
  };

  return (
    <ToggleButtonGroup value={alignment} exclusive onChange={handleAlignment} aria-label="text alignment">
      <ToggleButton value="left" aria-label="left aligned">
        <FormatAlignLeftIcon />
      </ToggleButton>
      <ToggleButton value="center" aria-label="centered">
        <FormatAlignCenterIcon />
      </ToggleButton>
      <ToggleButton value="right" aria-label="right aligned">
        <FormatAlignRightIcon />
      </ToggleButton>
      <ToggleButton value="justify" aria-label="justified" disabled>
        <FormatAlignJustifyIcon />
      </ToggleButton>
    </ToggleButtonGroup>
  );
}

export function MultipleSelection() {
  const [formats, setFormats] = React.useState<string[]>(() => ["bold"]);

  const handleFormat = (_event: React.MouseEvent<HTMLElement>, newFormats: string[]) => {
    setFormats(newFormats);
  };

  return (
    <ToggleButtonGroup value={formats} onChange={handleFormat} aria-label="text formatting">
      <ToggleButton value="bold" aria-label="bold">
        <FormatBoldIcon />
      </ToggleButton>
      <ToggleButton value="italic" aria-label="italic">
        <FormatItalicIcon />
      </ToggleButton>
      <ToggleButton value="underlined" aria-label="underlined">
        <FormatUnderlinedIcon />
      </ToggleButton>
    </ToggleButtonGroup>
  );
}

export function Sizes() {
  const [alignment, setAlignment] = React.useState("left");

  const handleChange = (_event: React.MouseEvent<HTMLElement>, newAlignment: string) => {
    if (newAlignment !== null) {
      setAlignment(newAlignment);
    }
  };

  const children = [
    <ToggleButton value="left" key="left" aria-label="left">
      <FormatAlignLeftIcon />
    </ToggleButton>,
    <ToggleButton value="center" key="center" aria-label="center">
      <FormatAlignCenterIcon />
    </ToggleButton>,
    <ToggleButton value="right" key="right" aria-label="right">
      <FormatAlignRightIcon />
    </ToggleButton>,
    <ToggleButton value="justify" key="justify" aria-label="justify">
      <FormatAlignJustifyIcon />
    </ToggleButton>,
  ];

  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        "& > *": {
          m: 1,
        },
      }}
    >
      <ToggleButtonGroup size="small" value={alignment} exclusive onChange={handleChange} aria-label="Small size">
        {children}
      </ToggleButtonGroup>
      <ToggleButtonGroup size="medium" value={alignment} exclusive onChange={handleChange} aria-label="Medium size">
        {children}
      </ToggleButtonGroup>
      <ToggleButtonGroup size="large" value={alignment} exclusive onChange={handleChange} aria-label="Large size">
        {children}
      </ToggleButtonGroup>
    </Box>
  );
}

export function Vertical() {
  const [view, setView] = React.useState("list");

  const handleChange = (_event: React.MouseEvent<HTMLElement>, nextView: string) => {
    if (nextView !== null) {
      setView(nextView);
    }
  };

  return (
    <ToggleButtonGroup orientation="vertical" value={view} exclusive onChange={handleChange}>
      <ToggleButton value="list" aria-label="list">
        <ViewListIcon />
      </ToggleButton>
      <ToggleButton value="module" aria-label="module">
        <ViewModuleIcon />
      </ToggleButton>
      <ToggleButton value="quilt" aria-label="quilt">
        <ViewQuiltIcon />
      </ToggleButton>
    </ToggleButtonGroup>
  );
}

export function Colors() {
  const [alignment, setAlignment] = React.useState("left");

  const handleChange = (_event: React.MouseEvent<HTMLElement>, newAlignment: string) => {
    if (newAlignment !== null) {
      setAlignment(newAlignment);
    }
  };

  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        "& > *": {
          m: 1,
        },
      }}
    >
      <ToggleButtonGroup color="primary" value={alignment} exclusive onChange={handleChange} aria-label="Primary color">
        <ToggleButton value="left" aria-label="left">
          <FormatAlignLeftIcon />
        </ToggleButton>
        <ToggleButton value="center" aria-label="center">
          <FormatAlignCenterIcon />
        </ToggleButton>
        <ToggleButton value="right" aria-label="right">
          <FormatAlignRightIcon />
        </ToggleButton>
      </ToggleButtonGroup>
      <ToggleButtonGroup
        color="secondary"
        value={alignment}
        exclusive
        onChange={handleChange}
        aria-label="Secondary color"
      >
        <ToggleButton value="left" aria-label="left">
          <FormatAlignLeftIcon />
        </ToggleButton>
        <ToggleButton value="center" aria-label="center">
          <FormatAlignCenterIcon />
        </ToggleButton>
        <ToggleButton value="right" aria-label="right">
          <FormatAlignRightIcon />
        </ToggleButton>
      </ToggleButtonGroup>
      <ToggleButtonGroup color="success" value={alignment} exclusive onChange={handleChange} aria-label="Success color">
        <ToggleButton value="left" aria-label="left">
          <FormatAlignLeftIcon />
        </ToggleButton>
        <ToggleButton value="center" aria-label="center">
          <FormatAlignCenterIcon />
        </ToggleButton>
        <ToggleButton value="right" aria-label="right">
          <FormatAlignRightIcon />
        </ToggleButton>
      </ToggleButtonGroup>
    </Box>
  );
}

export function StandaloneToggle() {
  const [selected, setSelected] = React.useState(false);

  return (
    <ToggleButton
      aria-label="Bold"
      value="check"
      selected={selected}
      onChange={() => {
        setSelected(!selected);
      }}
    >
      <FormatBoldIcon />
    </ToggleButton>
  );
}

export const InteractionTest: Story = {
  args: {} as never,
  render: () => {
    const [formats, setFormats] = React.useState<string[]>(() => ["bold"]);
    const handleFormat = fn((_event: React.MouseEvent<HTMLElement>, newFormats: string[]) => {
      setFormats(newFormats);
    });

    return (
      <Box data-testid="toggle-button-container">
        <ToggleButtonGroup value={formats} onChange={handleFormat} aria-label="text formatting">
          <ToggleButton value="bold" aria-label="bold">
            <FormatBoldIcon />
          </ToggleButton>
          <ToggleButton value="italic" aria-label="italic">
            <FormatItalicIcon />
          </ToggleButton>
          <ToggleButton value="underlined" aria-label="underlined">
            <FormatUnderlinedIcon />
          </ToggleButton>
        </ToggleButtonGroup>
      </Box>
    );
  },
  play: async ({ canvasElement, step }) => {
    const canvas = within(canvasElement);

    await step("Verify initial render with bold selected", async () => {
      const boldButton = canvas.getByRole("button", { name: /bold/i });
      const italicButton = canvas.getByRole("button", { name: /italic/i });
      const underlinedButton = canvas.getByRole("button", {
        name: /underlined/i,
      });

      await expect(boldButton).toBeInTheDocument();
      await expect(italicButton).toBeInTheDocument();
      await expect(underlinedButton).toBeInTheDocument();
      await expect(boldButton).toHaveAttribute("aria-pressed", "true");
      await expect(italicButton).toHaveAttribute("aria-pressed", "false");
    });

    await step("Toggle italic button on", async () => {
      const italicButton = canvas.getByRole("button", { name: /italic/i });
      await userEvent.click(italicButton);

      await expect(italicButton).toHaveAttribute("aria-pressed", "true");
      await expect(canvas.getByRole("button", { name: /bold/i })).toHaveAttribute("aria-pressed", "true");
    });

    await step("Toggle underlined button on", async () => {
      const underlinedButton = canvas.getByRole("button", {
        name: /underlined/i,
      });
      await userEvent.click(underlinedButton);

      await expect(underlinedButton).toHaveAttribute("aria-pressed", "true");
    });

    await step("Toggle bold button off", async () => {
      const boldButton = canvas.getByRole("button", { name: /bold/i });
      await userEvent.click(boldButton);

      await expect(boldButton).toHaveAttribute("aria-pressed", "false");
      await expect(canvas.getByRole("button", { name: /italic/i })).toHaveAttribute("aria-pressed", "true");
      await expect(canvas.getByRole("button", { name: /underlined/i })).toHaveAttribute("aria-pressed", "true");
    });

    await step("Toggle all buttons off", async () => {
      await userEvent.click(canvas.getByRole("button", { name: /italic/i }));
      await userEvent.click(canvas.getByRole("button", { name: /underlined/i }));

      await expect(canvas.getByRole("button", { name: /bold/i })).toHaveAttribute("aria-pressed", "false");
      await expect(canvas.getByRole("button", { name: /italic/i })).toHaveAttribute("aria-pressed", "false");
      await expect(canvas.getByRole("button", { name: /underlined/i })).toHaveAttribute("aria-pressed", "false");
    });
  },
};
