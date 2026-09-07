/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Chip/Chip.stories.tsx
 * Upstream project license: MIT
 */
import DeleteIcon from "@mui/icons-material/Delete";
import DoneIcon from "@mui/icons-material/Done";
import TagFacesIcon from "@mui/icons-material/TagFaces";
import Chip from "@mui/material/Chip";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import { styled } from "@mui/material/styles";
import type { Meta, StoryObj } from "@storybook/react-vite";
import React from "react";
import { expect, userEvent, within } from "storybook/test";
import {
  createBooleanArgType,
  createSelectArgType,
  muiDisabledArgType,
  muiSizeArgType,
  muiVariantArgType,
} from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Data Display/Chip",
  component: Chip,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    color: createSelectArgType(
      ["primary", "secondary", "success", "error", "info", "warning", "default", "callToAction"] satisfies NonNullable<
        React.ComponentProps<typeof Chip>["color"]
      >[],
      "default",
      "The color of the component.",
      "Appearance",
    ),
    size: muiSizeArgType,
    disabled: muiDisabledArgType,
    variant: muiVariantArgType(["filled", "outlined"], "filled"),
    clickable: createBooleanArgType("If true, the chip will appear clickable.", false, "Behavior"),
    label: {
      control: "text",
      description: "The content of the label.",
      table: { category: "Content" },
    },
    // Disable props that require JSX
    avatar: { control: false },
    deleteIcon: { control: false },
    icon: { control: false },
  },
} satisfies Meta<typeof Chip>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: {
    label: "Chip Label",
    color: "primary",
    size: "medium",
    variant: "filled",
    disabled: false,
    clickable: false,
  },
};

export const Default: Story = {
  args: {
    label: "Default Chip",
  },
};

export const BasicChip: Story = {
  render: () => (
    <Stack direction="row" spacing={1}>
      <Chip label="Chip Filled" />
      <Chip label="Chip Outlined" variant="outlined" />
    </Stack>
  ),
};

export function ColorChips() {
  return (
    <Stack spacing={1} sx={{ alignItems: "center" }}>
      <Stack direction="row" spacing={1}>
        <Chip label="primary" color="primary" />
        <Chip label="success" color="success" />
      </Stack>
      <Stack direction="row" spacing={1}>
        <Chip label="primary" color="primary" variant="outlined" />
        <Chip label="success" color="success" variant="outlined" />
      </Stack>
    </Stack>
  );
}

export const Clickable: Story = {
  render: () => {
    const [clicked, setClicked] = React.useState(false);
    const handleClick = () => setClicked(true);

    return (
      <Stack direction="row" spacing={1}>
        <Chip label={clicked ? "Clicked" : "Clickable"} onClick={handleClick} />
        <Chip label={clicked ? "Clicked" : "Clickable"} variant="outlined" onClick={handleClick} />
      </Stack>
    );
  },
};

export function Deletable() {
  const [deleted, setDeleted] = React.useState(false);
  const handleDelete = () => setDeleted(true);
  if (deleted) return <p role="status">Chips deleted</p>;

  return (
    <Stack direction="row" spacing={1}>
      <Chip label="Deletable" onDelete={handleDelete} />
      <Chip label="Deletable" variant="outlined" onDelete={handleDelete} />
    </Stack>
  );
}

export function ClickableAndDeletable() {
  const handleClick = () => {
    console.info("You clicked the Chip.");
  };

  const handleDelete = () => {
    console.info("You clicked the delete icon.");
  };

  return (
    <Stack direction="row" spacing={1}>
      <Chip label="Clickable Deletable" onClick={handleClick} onDelete={handleDelete} />
      <Chip label="Clickable Deletable" variant="outlined" onClick={handleClick} onDelete={handleDelete} />
    </Stack>
  );
}

export function ClickableLink() {
  return (
    <Stack direction="row" spacing={1}>
      <Chip label="Clickable Link" component={"a" as const} {...{ href: "#basic-chip" }} clickable />
      <Chip label="Clickable Link" component={"a" as const} {...{ href: "#basic-chip" }} variant="outlined" clickable />
    </Stack>
  );
}

export function CustomDeleteIcon() {
  const handleClick = () => {
    console.info("You clicked the Chip.");
  };

  const handleDelete = () => {
    console.info("You clicked the delete icon.");
  };

  return (
    <Stack direction="row" spacing={1}>
      <Chip label="Custom delete icon" onClick={handleClick} onDelete={handleDelete} deleteIcon={<DoneIcon />} />
      <Chip
        label="Custom delete icon"
        onClick={handleClick}
        onDelete={handleDelete}
        deleteIcon={<DeleteIcon />}
        variant="outlined"
      />
    </Stack>
  );
}

export function SizesChips() {
  return (
    <Stack direction="row" spacing={1}>
      <Chip label="Small" size="small" />
      <Chip label="Small" size="small" variant="outlined" />
    </Stack>
  );
}

const ListItem = styled("li")(({ theme }) => ({
  margin: theme.spacing(0.5),
}));

export function ChipsArray() {
  const [chipData, setChipData] = React.useState([
    { key: 0, label: "Angular" },
    { key: 1, label: "jQuery" },
    { key: 2, label: "Polymer" },
    { key: 3, label: "React" },
    { key: 4, label: "Vue.js" },
  ]);

  const handleDelete = (chipToDelete: { key: number; label?: string }) => () => {
    setChipData((chips) => chips.filter((chip) => chip.key !== chipToDelete.key));
  };

  return (
    <Paper
      sx={{
        display: "flex",
        justifyContent: "center",
        flexWrap: "wrap",
        listStyle: "none",
        p: 0.5,
        m: 0,
      }}
      component="ul"
    >
      {chipData.map((data) => {
        let icon: React.ReactElement | undefined;

        if (data.label === "React") {
          icon = <TagFacesIcon />;
        }

        return (
          <ListItem key={data.key}>
            <Chip icon={icon} label={data.label} onDelete={data.label === "React" ? undefined : handleDelete(data)} />
          </ListItem>
        );
      })}
    </Paper>
  );
}

export const InteractionTest: Story = {
  args: {},
  render: () => <ChipsArray />,
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);

    const chip = canvas.getByRole("button", { name: "Angular" });
    await userEvent.click(chip);
    await userEvent.keyboard("{Delete}");
    await expect(canvas.queryByText("Angular")).not.toBeInTheDocument();
    await expect(canvas.getByText("React")).toBeVisible();
  },
};
