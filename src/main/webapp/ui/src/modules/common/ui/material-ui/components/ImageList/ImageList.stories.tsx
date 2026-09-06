/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/ImageList/ImageList.stories.tsx
 * Upstream project license: MIT
 */
import InfoIcon from "@mui/icons-material/Info";
import IconButton from "@mui/material/IconButton";
import ImageList from "@mui/material/ImageList";
import ImageListItem from "@mui/material/ImageListItem";
import ImageListItemBar from "@mui/material/ImageListItemBar";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, within } from "storybook/test";
import { createNumberArgType, createSelectArgType } from "../../argTypeTemplates";

/**
 * Sample image data for ImageList stories
 */
const showImageInfo = fn();

const sampleImage = (index: number) =>
  `data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="400" height="${240 + (index % 3) * 80}"><rect width="100%" height="100%" fill="${["#90caf9", "#a5d6a7", "#ffcc80"][index % 3]}"/></svg>`)}`;

const itemData = [
  {
    img: sampleImage(0),
    title: "Breakfast",
    rows: 2,
    cols: 2,
  },
  {
    img: sampleImage(1),
    title: "Burger",
  },
  {
    img: sampleImage(2),
    title: "Camera",
  },
  {
    img: sampleImage(3),
    title: "Coffee",
    cols: 2,
  },
  {
    img: sampleImage(4),
    title: "Hats",
    rows: 2,
    cols: 2,
  },
  {
    img: sampleImage(5),
    title: "Honey",
  },
  {
    img: sampleImage(6),
    title: "Basketball",
  },
  {
    img: sampleImage(7),
    title: "Fern",
  },
  {
    img: sampleImage(8),
    title: "Mushrooms",
    rows: 2,
    cols: 2,
  },
  {
    img: sampleImage(9),
    title: "Tomato basil",
  },
  {
    img: sampleImage(10),
    title: "Sea star",
  },
  {
    img: sampleImage(11),
    title: "Bike",
    cols: 2,
  },
];

const meta: Meta<typeof ImageList> = {
  title: "Material UI/Data Display/ImageList",
  component: ImageList,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    variant: createSelectArgType(
      ["standard", "quilted", "woven", "masonry"],
      "standard",
      "The variant to use.",
      "Appearance",
    ),
    cols: createNumberArgType("Number of columns.", 3, 1, 6, "Layout"),
    rowHeight: {
      control: { type: "number", min: 50, max: 500 },
      description: "The height of one row in px.",
      table: {
        defaultValue: { summary: "auto" },
        category: "Layout",
        type: { summary: 'number | "auto"' },
      },
    },
    gap: createNumberArgType("The gap between items in px.", 4, 0, 24, "Layout"),
    children: { control: false },
  },
};

export default meta;
type Story = StoryObj<typeof ImageList>;

export const Playground: Story = {
  args: {
    variant: "standard",
    cols: 3,
    rowHeight: 164,
    gap: 4,
  },
  render: (args) => (
    <ImageList tabIndex={0} aria-label="Image gallery" sx={{ width: 500, height: 450 }} {...args}>
      {itemData.map((item) => (
        <ImageListItem key={item.title}>
          <img src={item.img} alt={item.title} loading="lazy" />
        </ImageListItem>
      ))}
    </ImageList>
  ),
};

/**
 * Default ImageList with basic configuration
 */
export const Default: Story = {
  args: {
    sx: { width: 500, height: 450 },
    cols: 3,
    rowHeight: 164,
  },
  render: (args) => (
    <ImageList tabIndex={0} aria-label="Image gallery" {...args}>
      {itemData.map((item) => (
        <ImageListItem key={item.title}>
          <img src={item.img} alt={item.title} loading="lazy" />
        </ImageListItem>
      ))}
    </ImageList>
  ),
};

/**
 * Standard ImageList displaying images in a basic grid layout
 */
export const Standard: Story = {
  render: () => (
    <ImageList tabIndex={0} aria-label="Image gallery" sx={{ width: 500, height: 450 }} cols={3} rowHeight={164}>
      {itemData.map((item) => (
        <ImageListItem key={item.title}>
          <img src={item.img} alt={item.title} loading="lazy" />
        </ImageListItem>
      ))}
    </ImageList>
  ),
};

/**
 * Quilted ImageList with images spanning multiple rows/columns
 */
export const Quilted: Story = {
  render: () => (
    <ImageList
      tabIndex={0}
      aria-label="Image gallery"
      sx={{ width: 500, height: 450 }}
      variant="quilted"
      cols={4}
      rowHeight={121}
    >
      {itemData.map((item) => (
        <ImageListItem key={item.title} cols={item.cols || 1} rows={item.rows || 1}>
          <img src={item.img} alt={item.title} loading="lazy" />
        </ImageListItem>
      ))}
    </ImageList>
  ),
};

/**
 * Woven ImageList with alternating image heights
 */
export const Woven: Story = {
  render: () => (
    <ImageList
      tabIndex={0}
      aria-label="Image gallery"
      sx={{ width: 500, height: 450 }}
      variant="woven"
      cols={3}
      gap={8}
    >
      {itemData.map((item) => (
        <ImageListItem key={item.title}>
          <img src={item.img} alt={item.title} loading="lazy" />
        </ImageListItem>
      ))}
    </ImageList>
  ),
};

/**
 * Masonry ImageList with variable height images
 */
export const Masonry: Story = {
  render: () => (
    <ImageList
      tabIndex={0}
      aria-label="Image gallery"
      sx={{ width: 500, height: 450 }}
      variant="masonry"
      cols={3}
      gap={8}
    >
      {itemData.map((item) => (
        <ImageListItem key={item.title}>
          <img src={item.img} alt={item.title} loading="lazy" />
        </ImageListItem>
      ))}
    </ImageList>
  ),
};

/**
 * ImageList with title bars and action icons
 */
export const WithTitlebar: Story = {
  render: () => (
    <ImageList tabIndex={0} aria-label="Image gallery" sx={{ width: 500, height: 450 }}>
      {itemData.map((item) => (
        <ImageListItem key={item.title}>
          <img src={item.img} alt="" loading="lazy" />
          <ImageListItemBar
            title={item.title}
            subtitle={<span>by: Unknown</span>}
            actionIcon={
              <IconButton
                onClick={() => showImageInfo(item.title)}
                sx={{ color: "white" }}
                aria-label={`info about ${item.title}`}
              >
                <InfoIcon />
              </IconButton>
            }
          />
        </ImageListItem>
      ))}
    </ImageList>
  ),
  play: async ({ canvasElement }) => {
    showImageInfo.mockClear();
    await userEvent.click(within(canvasElement).getByRole("button", { name: "info about Breakfast" }));
    await expect(showImageInfo).toHaveBeenCalledWith("Breakfast");
  },
};

/**
 * ImageList with title bars positioned below images
 */
export const WithTitlebarBelow: Story = {
  render: () => (
    <ImageList tabIndex={0} aria-label="Image gallery" sx={{ width: 500, height: 450 }}>
      {itemData.map((item) => (
        <ImageListItem key={item.title}>
          <img src={item.img} alt="" loading="lazy" />
          <ImageListItemBar
            title={item.title}
            position="below"
            actionIcon={
              <IconButton onClick={() => showImageInfo(item.title)} aria-label={`info about ${item.title}`}>
                <InfoIcon />
              </IconButton>
            }
          />
        </ImageListItem>
      ))}
    </ImageList>
  ),
};
