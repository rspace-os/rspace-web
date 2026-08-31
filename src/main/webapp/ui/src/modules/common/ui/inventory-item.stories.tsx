import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { expect, within } from "storybook/test";
import { InventoryItem, InventoryLocationLink } from "./inventory-item";
import { ItemGroup } from "./item";

const meta = {
  title: "DesignSystem/InventoryItem",
  component: InventoryItem,
  tags: ["autodocs"],
} satisfies Meta<typeof InventoryItem>;

export default meta;

type Story = StoryObj<typeof meta>;

const confocal = {
  name: "Zeiss LSM 900 confocal",
  globalId: "IC-LSM900",
  href: "/inventory/IC-LSM900",
  idLinkLabel: "Open inventory record IC-LSM900",
};

const freezer = {
  name: "Biobank ultra-low freezer",
  globalId: "IC-COLD4",
  href: "/inventory/IC-COLD4",
  idLinkLabel: "Open inventory record IC-COLD4",
};

export const IdInTitle: Story = {
  args: {
    ...confocal,
    idPlacement: "title",
    variant: "outline",
    children: <InventoryLocationLink name="Lab 2.14" globalId="IC456" />,
  },
  render: (args) => (
    <ItemGroup style={{ width: "420px" }}>
      <InventoryItem {...args} />
    </ItemGroup>
  ),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const link = canvas.getByRole("link", {
      name: "Open inventory record IC-LSM900",
    });
    expect(link).toHaveAttribute("href", "/inventory/IC-LSM900");
    const locationLink = canvas.getByRole("link", { name: "Lab 2.14" });
    expect(locationLink).toHaveAttribute("href", "/globalId/IC456");
    expect(locationLink.querySelector(".lucide-external-link")).toBeInTheDocument();

    const title = canvasElement.querySelector('[data-slot="item-title"]');
    expect(title).toContainElement(link);
  },
};

export const IdInDescription: Story = {
  args: {
    ...confocal,
    variant: "outline",
    children: <InventoryLocationLink name="Lab 2.14" globalId="IC456" />,
  },
  render: (args) => (
    <ItemGroup style={{ width: "420px" }}>
      <InventoryItem {...args} />
    </ItemGroup>
  ),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const link = canvas.getByRole("link", {
      name: "Open inventory record IC-LSM900",
    });
    expect(link).toHaveAttribute("href", "/inventory/IC-LSM900");

    const title = canvasElement.querySelector('[data-slot="item-title"]');
    expect(title).not.toContainElement(link);
    const description = canvasElement.querySelector('[data-slot="item-description"]');
    expect(description).toContainElement(link);
  },
};

export const Compact: Story = {
  args: {
    ...confocal,
    compact: true,
    variant: "outline",
    children: <InventoryLocationLink name="Lab 2.14" globalId="IC456" />,
  },
  render: (args) => (
    <ItemGroup style={{ width: "420px" }}>
      <InventoryItem {...args} />
      <InventoryItem {...freezer} compact variant="outline" />
    </ItemGroup>
  ),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const link = canvas.getByRole("link", { name: "Open inventory record IC-LSM900" });
    const title = canvasElement.querySelector('[data-slot="item-title"]');

    expect(title).toContainElement(link);
    expect(canvasElement.querySelector('[data-slot="item-description"]')).toBeNull();
    expect(canvas.queryByText("Lab 2.14")).not.toBeInTheDocument();

    const [item] = canvas.getAllByRole("listitem");
    expect(title?.getBoundingClientRect().height).toBeLessThanOrEqual(item.getBoundingClientRect().height);
  },
};

export const WithBackground: Story = {
  args: {
    ...confocal,
    variant: "filled",
    children: <InventoryLocationLink name="Lab 2.14" globalId="IC456" />,
  },
  parameters: {
    docs: {
      description: {
        story:
          "Uses the semantic secondary background and foreground tokens, which provide separate light and dark mode colors.",
      },
    },
  },
  render: (args) => (
    <ItemGroup style={{ width: "420px" }}>
      <InventoryItem {...args} />
    </ItemGroup>
  ),
};

export const IconAndBadgeBackground: Story = {
  args: {
    ...confocal,
    variant: "accented",
    children: <InventoryLocationLink name="Lab 2.14" globalId="IC456" />,
  },
  parameters: {
    docs: {
      description: {
        story: "Applies the semantic secondary background and foreground tokens only to the icon and global-ID badge.",
      },
    },
  },
  render: (args) => (
    <ItemGroup style={{ width: "420px" }}>
      <InventoryItem {...args} />
    </ItemGroup>
  ),
};

export const VariantComparison: Story = {
  args: confocal,
  render: () => (
    <div style={{ display: "flex", flexDirection: "column", gap: "24px" }}>
      <ItemGroup style={{ width: "420px" }}>
        <InventoryItem {...confocal} idPlacement="title" variant="outline">
          <InventoryLocationLink name="Lab 2.14" globalId="IC456" />
        </InventoryItem>
        <InventoryItem {...freezer} idPlacement="title" variant="outline">
          <InventoryLocationLink name="Biobank freezer F-2" globalId="IC457" />
        </InventoryItem>
      </ItemGroup>
      <ItemGroup style={{ width: "420px" }}>
        <InventoryItem {...confocal} variant="outline">
          <InventoryLocationLink name="Lab 2.14" globalId="IC456" />
        </InventoryItem>
        <InventoryItem {...freezer} variant="outline">
          <InventoryLocationLink name="Biobank freezer F-2" globalId="IC457" />
        </InventoryItem>
      </ItemGroup>
    </div>
  ),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getAllByRole("link", { name: /Open inventory record/ })).toHaveLength(4);
  },
};

export const LocationFixtures: Story = {
  args: confocal,
  render: () => (
    <ItemGroup style={{ width: "420px" }}>
      <InventoryItem {...confocal} variant="outline">
        <InventoryLocationLink name="Imaging lab" globalId="IC456" />
      </InventoryItem>
      <InventoryItem {...freezer} variant="outline">
        <InventoryLocationLink name={null} globalId={null} />
      </InventoryItem>
      <InventoryItem
        {...confocal}
        name="Flow cytometer"
        globalId="IN126"
        idLinkLabel="Open inventory record IN126"
        variant="outline"
      >
        <InventoryLocationLink name="Cell analysis facility with a deliberately long location name" globalId="IC458" />
      </InventoryItem>
    </ItemGroup>
  ),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    expect(canvas.getByRole("link", { name: "Imaging lab" })).toHaveAttribute("href", "/globalId/IC456");
    expect(canvas.getByRole("link", { name: /Cell analysis facility/ })).toHaveAttribute("href", "/globalId/IC458");
    expect(canvas.getAllByRole("listitem")).toHaveLength(3);
  },
};

export const LongName: Story = {
  args: {
    ...confocal,
    name: "Zeiss LSM 900 confocal with Airyscan 2 detector and environmental chamber",
    idPlacement: "title",
    variant: "outline",
    children: <InventoryLocationLink name="Imaging suite 1.02" globalId="IC458" />,
  },
  render: (args) => (
    <ItemGroup style={{ width: "420px" }}>
      <InventoryItem {...args} />
    </ItemGroup>
  ),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const link = canvas.getByRole("link", {
      name: "Open inventory record IC-LSM900",
    });
    const item = canvas.getByRole("listitem");

    const itemRight = item.getBoundingClientRect().right;
    expect(link.getBoundingClientRect().right).toBeLessThanOrEqual(itemRight + 1);
  },
};
