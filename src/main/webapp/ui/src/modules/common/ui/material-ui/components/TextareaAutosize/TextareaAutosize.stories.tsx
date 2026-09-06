/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/TextareaAutosize/TextareaAutosize.stories.tsx
 * Upstream project license: MIT
 */
import Box from "@mui/material/Box";
import Stack from "@mui/material/Stack";
import { styled } from "@mui/material/styles";
import TextareaAutosize from "@mui/material/TextareaAutosize";
import Typography from "@mui/material/Typography";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, userEvent, waitFor, within } from "storybook/test";
import { createNumberArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Inputs/TextareaAutosize",
  component: TextareaAutosize,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },

  args: { "aria-label": "Notes" },
  argTypes: {
    minRows: createNumberArgType("Minimum number of rows.", 1, 1, 20),
    maxRows: createNumberArgType("Maximum number of rows (before scrolling).", 0, 0, 50),
    placeholder: {
      control: "text",
      description: "Placeholder text.",
      table: { category: "Content" },
    },
    defaultValue: {
      control: "text",
      description: "Default value for uncontrolled component.",
      table: { category: "Content" },
    },
    disabled: {
      control: "boolean",
      description: "If true, the textarea is disabled.",
      table: {
        defaultValue: { summary: "false" },
        category: "State",
        type: { summary: "boolean" },
      },
    },
  },
} satisfies Meta<typeof TextareaAutosize>;

export default meta;
type Story = StoryObj<typeof meta>;

const StyledTextarea = styled(TextareaAutosize)(({ theme }) => ({
  width: "100%",
  fontFamily: theme.typography.fontFamily,
  fontSize: theme.typography.body1.fontSize,
  fontWeight: 400,
  lineHeight: 1.5,
  padding: "8px 12px",
  borderRadius: "8px",
  color: theme.palette.text.primary,
  background: theme.palette.background.paper,
  border: `1px solid ${theme.palette.divider}`,
  boxShadow: `0px 2px 2px ${theme.palette.action.focus}`,
  resize: "none",
  "&:hover": {
    borderColor: theme.palette.primary.main,
  },
  "&:focus": {
    borderColor: theme.palette.primary.main,
    boxShadow: `0 0 0 3px ${theme.palette.primary.light}`,
    outline: "none",
  },
  "&:focus-visible": {
    outline: 0,
  },
}));

export const Default: Story = {
  args: {
    minRows: 3,
    placeholder: "Type something...",
    style: { width: 300 },
  },
};

export const MinRows: Story = {
  args: {},
  render: () => (
    <Stack spacing={2} sx={{ width: 300 }}>
      <Box>
        <Typography variant="caption">minRows: 2</Typography>
        <TextareaAutosize aria-label="Notes" minRows={2} placeholder="Minimum 2 rows" />
      </Box>
      <Box>
        <Typography variant="caption">minRows: 4</Typography>
        <TextareaAutosize aria-label="Notes" minRows={4} placeholder="Minimum 4 rows" />
      </Box>
      <Box>
        <Typography variant="caption">minRows: 6</Typography>
        <TextareaAutosize aria-label="Notes" minRows={6} placeholder="Minimum 6 rows" />
      </Box>
    </Stack>
  ),
};

export const MaxRows: Story = {
  args: {},
  render: () => (
    <Stack spacing={2} sx={{ width: 300 }}>
      <Box>
        <Typography variant="caption">maxRows: 4 (scroll after)</Typography>
        <TextareaAutosize
          aria-label="Notes"
          minRows={2}
          maxRows={4}
          placeholder="Type many lines to see scrolling..."
        />
      </Box>
    </Stack>
  ),
};

export const Styled: Story = {
  args: {},
  render: () => (
    <Box sx={{ width: 400 }}>
      <StyledTextarea aria-label="Notes" minRows={3} placeholder="This textarea has custom styling applied..." />
    </Box>
  ),
};

export const WithDefaultValue: Story = {
  args: {
    minRows: 3,
    defaultValue: "This is some default text that was pre-filled.\n\nYou can edit it as needed.",
    style: { width: 300 },
  },
};

export const Disabled: Story = {
  args: {
    minRows: 3,
    disabled: true,
    defaultValue: "This textarea is disabled",
    style: { width: 300 },
  },
};

export const AutoGrow: Story = {
  args: {},
  render: () => (
    <Box sx={{ width: 400 }}>
      <Typography variant="body2" sx={{ mb: 1 }}>
        Start typing to see the textarea grow:
      </Typography>
      <StyledTextarea aria-label="Notes" minRows={1} placeholder="This will grow as you type..." />
    </Box>
  ),
};

export const FormExample: Story = {
  args: {},
  render: () => (
    <Box
      component="form"
      sx={{
        width: 400,
        display: "flex",
        flexDirection: "column",
        gap: 2,
      }}
    >
      <Box>
        <Typography component="label" htmlFor="description" variant="subtitle2" sx={{ mb: 0.5, display: "block" }}>
          Description
        </Typography>
        <StyledTextarea id="description" minRows={3} placeholder="Enter a detailed description..." />
      </Box>
      <Box>
        <Typography component="label" htmlFor="comments" variant="subtitle2" sx={{ mb: 0.5, display: "block" }}>
          Additional Comments
        </Typography>
        <StyledTextarea id="comments" minRows={2} maxRows={6} placeholder="Any additional comments..." />
      </Box>
    </Box>
  ),
};

export const InteractionTest: Story = {
  args: {
    minRows: 2,
    placeholder: "Test textarea",
    style: { width: 300 },
    "aria-label": "test textarea",
  },
  play: async ({ canvasElement, step }) => {
    const canvas = within(canvasElement);

    await step("Verify textarea is rendered", async () => {
      const textarea = canvas.getByRole("textbox");
      await expect(textarea).toBeInTheDocument();
      await expect(textarea).toHaveAttribute("placeholder", "Test textarea");
    });

    await step("Type text into textarea", async () => {
      const textarea = canvas.getByRole("textbox");
      await userEvent.type(textarea, "Hello, World!");
      await expect(textarea).toHaveValue("Hello, World!");
    });

    await step("Add multiple lines", async () => {
      const textarea = canvas.getByRole("textbox");
      const initialHeight = textarea.getBoundingClientRect().height;
      await userEvent.type(textarea, "\nLine 2\nLine 3\nLine 4");
      await waitFor(() => expect(textarea.getBoundingClientRect().height).toBeGreaterThan(initialHeight));
      await expect(textarea).toHaveValue("Hello, World!\nLine 2\nLine 3\nLine 4");
    });

    await step("Clear and type new content", async () => {
      const textarea = canvas.getByRole("textbox");
      await userEvent.clear(textarea);
      await userEvent.type(textarea, "New content");
      await expect(textarea).toHaveValue("New content");
    });
  },
};
