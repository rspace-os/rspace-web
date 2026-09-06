/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Divider/Divider.stories.tsx
 * Upstream project license: MIT
 */
import FormatAlignCenterIcon from "@mui/icons-material/FormatAlignCenter";
import FormatAlignLeftIcon from "@mui/icons-material/FormatAlignLeft";
import FormatAlignRightIcon from "@mui/icons-material/FormatAlignRight";
import Box from "@mui/material/Box";
import Chip from "@mui/material/Chip";
import Divider from "@mui/material/Divider";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemText from "@mui/material/ListItemText";
import Typography from "@mui/material/Typography";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { createBooleanArgType, createSelectArgType, muiOrientationArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Data Display/Divider",
  component: Divider,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    orientation: muiOrientationArgType,
    variant: createSelectArgType(["fullWidth", "inset", "middle"], "fullWidth", "The variant to use.", "Appearance"),
    textAlign: createSelectArgType(["center", "left", "right"], "center", "The text alignment.", "Layout"),
    flexItem: createBooleanArgType(
      "If true, a vertical divider will have the correct height when used in flex container.",
      false,
      "Layout",
    ),
    children: { control: false },
  },
} satisfies Meta<typeof Divider>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: {
    orientation: "horizontal",
    variant: "fullWidth",
    textAlign: "center",
    flexItem: false,
  },
  render: (args) => (
    <Box
      sx={{
        width: "100%",
        maxWidth: 360,
        display: "flex",
        flexDirection: args.orientation === "vertical" ? "row" : "column",
        gap: 1,
      }}
    >
      <Typography variant="body1">Item 1</Typography>
      <Divider {...args} flexItem={args.orientation === "vertical" || args.flexItem} />
      <Typography variant="body1">Item 2</Typography>
    </Box>
  ),
};

export const Default: Story = {
  args: {},
  render: (args) => (
    <Box sx={{ width: "100%", maxWidth: 360 }}>
      <Typography variant="body1">Item 1</Typography>
      <Divider {...args} />
      <Typography variant="body1">Item 2</Typography>
      <Divider {...args} />
      <Typography variant="body1">Item 3</Typography>
    </Box>
  ),
};

export const FullWidth: Story = {
  render: () => (
    <Box sx={{ width: "100%", maxWidth: 360, bgcolor: "background.paper" }}>
      <List aria-label="mailbox folders">
        <ListItem>
          <ListItemText primary="Inbox" />
        </ListItem>
        <Divider aria-hidden="true" role="presentation" component="li" />
        <ListItem>
          <ListItemText primary="Drafts" />
        </ListItem>
        <Divider aria-hidden="true" role="presentation" component="li" />
        <ListItem>
          <ListItemText primary="Trash" />
        </ListItem>
        <Divider aria-hidden="true" role="presentation" component="li" />
        <ListItem>
          <ListItemText primary="Spam" />
        </ListItem>
      </List>
    </Box>
  ),
};

export const Inset: Story = {
  render: () => (
    <Box sx={{ width: "100%", maxWidth: 360, bgcolor: "background.paper" }}>
      <List aria-label="mailbox folders">
        <ListItem>
          <ListItemText primary="Inbox" />
        </ListItem>
        <Divider aria-hidden="true" role="presentation" component="li" variant="inset" />
        <ListItem>
          <ListItemText primary="Drafts" />
        </ListItem>
        <Divider aria-hidden="true" role="presentation" component="li" variant="inset" />
        <ListItem>
          <ListItemText primary="Trash" />
        </ListItem>
        <Divider aria-hidden="true" role="presentation" component="li" variant="inset" />
        <ListItem>
          <ListItemText primary="Spam" />
        </ListItem>
      </List>
    </Box>
  ),
};

export const Middle: Story = {
  render: () => (
    <Box sx={{ width: "100%", maxWidth: 360, bgcolor: "background.paper" }}>
      <List aria-label="mailbox folders">
        <ListItem>
          <ListItemText primary="Inbox" />
        </ListItem>
        <Divider aria-hidden="true" role="presentation" component="li" variant="middle" />
        <ListItem>
          <ListItemText primary="Drafts" />
        </ListItem>
        <Divider aria-hidden="true" role="presentation" component="li" variant="middle" />
        <ListItem>
          <ListItemText primary="Trash" />
        </ListItem>
        <Divider aria-hidden="true" role="presentation" component="li" variant="middle" />
        <ListItem>
          <ListItemText primary="Spam" />
        </ListItem>
      </List>
    </Box>
  ),
};

export const Vertical: Story = {
  render: () => (
    <Box
      sx={{
        display: "flex",
        alignItems: "center",
        width: "fit-content",
        border: (theme) => `1px solid ${theme.palette.divider}`,
        borderRadius: 1,
        bgcolor: "background.paper",
        color: "text.secondary",
        "& svg": {
          m: 1.5,
        },
        "& hr": {
          mx: 0.5,
        },
      }}
    >
      <FormatAlignLeftIcon />
      <Divider orientation="vertical" flexItem />
      <FormatAlignCenterIcon />
      <Divider orientation="vertical" flexItem />
      <FormatAlignRightIcon />
    </Box>
  ),
};

export const WithText: Story = {
  render: () => (
    <Box sx={{ width: "100%", maxWidth: 500 }}>
      <Typography variant="body1" gutterBottom>
        Content above the divider
      </Typography>
      <Divider>CENTER</Divider>
      <Typography variant="body1" gutterBottom sx={{ mt: 2 }}>
        Content below the center divider
      </Typography>
      <Divider textAlign="left">LEFT</Divider>
      <Typography variant="body1" gutterBottom sx={{ mt: 2 }}>
        Content below the left divider
      </Typography>
      <Divider textAlign="right">RIGHT</Divider>
      <Typography variant="body1" sx={{ mt: 2 }}>
        Content below the right divider
      </Typography>
    </Box>
  ),
};

export const WithChip: Story = {
  render: () => (
    <Box sx={{ width: "100%", maxWidth: 500 }}>
      <Typography variant="body1" gutterBottom>
        Lorem ipsum dolor sit amet, consectetur adipiscing elit.
      </Typography>
      <Divider>
        <Chip label="CHIP" size="small" />
      </Divider>
      <Typography variant="body1" sx={{ mt: 2 }}>
        Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
      </Typography>
      <Divider>
        <Chip label="OR" size="small" />
      </Divider>
      <Typography variant="body1" sx={{ mt: 2 }}>
        Ut enim ad minim veniam, quis nostrud exercitation.
      </Typography>
    </Box>
  ),
};

export const ListDividers: Story = {
  render: () => (
    <Box sx={{ width: "100%", maxWidth: 360, bgcolor: "background.paper" }}>
      <List>
        <ListItem>
          <ListItemText primary="Photos" secondary="Jan 9, 2024" />
        </ListItem>
        <Divider aria-hidden="true" role="presentation" component="li" />
        <ListItem>
          <ListItemText primary="Work" secondary="Jan 7, 2024" />
        </ListItem>
        <Divider aria-hidden="true" role="presentation" component="li" />
        <ListItem>
          <ListItemText primary="Vacation" secondary="July 20, 2023" />
        </ListItem>
      </List>
    </Box>
  ),
};

export const CustomStyles: Story = {
  render: () => (
    <Box sx={{ width: "100%", maxWidth: 500 }}>
      <Typography variant="body1" gutterBottom>
        Default divider
      </Typography>
      <Divider />
      <Typography variant="body1" gutterBottom sx={{ mt: 2 }}>
        Thick divider
      </Typography>
      <Divider sx={{ borderBottomWidth: 3 }} />
      <Typography variant="body1" gutterBottom sx={{ mt: 2 }}>
        Custom color divider
      </Typography>
      <Divider sx={{ borderColor: "primary.main", borderBottomWidth: 2 }} />
      <Typography variant="body1" gutterBottom sx={{ mt: 2 }}>
        Dashed divider
      </Typography>
      <Divider sx={{ borderStyle: "dashed" }} />
      <Typography variant="body1" gutterBottom sx={{ mt: 2 }}>
        Dotted divider
      </Typography>
      <Divider sx={{ borderStyle: "dotted", borderBottomWidth: 2 }} />
    </Box>
  ),
};

export const InteractionTest: Story = {
  render: () => (
    <Box sx={{ width: "100%", maxWidth: 360 }}>
      <Typography variant="body1">Item 1</Typography>
      <Divider role="separator" />
      <Typography variant="body1">Item 2</Typography>
      <Divider role="separator" />
      <Typography variant="body1">Item 3</Typography>
    </Box>
  ),
  play: async ({ canvasElement, step }) => {
    const canvas = within(canvasElement);

    await step("Verify dividers render", async () => {
      const dividers = canvas.getAllByRole("separator");
      await expect(dividers).toHaveLength(2);
    });

    await step("Verify content separation", async () => {
      const item1 = canvas.getByText("Item 1");
      const item2 = canvas.getByText("Item 2");
      const item3 = canvas.getByText("Item 3");
      await expect(item1).toBeInTheDocument();
      await expect(item2).toBeInTheDocument();
      await expect(item3).toBeInTheDocument();
    });

    await step("Test accessibility", async () => {
      const dividers = canvas.getAllByRole("separator");
      for (const divider of dividers) {
        await expect(divider.tagName).toBe("HR");
      }
    });
  },
};
