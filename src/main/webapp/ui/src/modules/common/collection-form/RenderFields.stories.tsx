import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, userEvent, within } from "storybook/test";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { InventoryRelationshipStory, RenderFieldsStory } from "./RenderFields.story";

const meta = {
  title: "Components/Collection Form/Render Fields",
  component: RenderFieldsStory,
  tags: ["autodocs"],
  decorators: [
    (Story) => (
      <I18nRoot namespaces={["common"]}>
        <Story />
      </I18nRoot>
    ),
  ],
} satisfies Meta<typeof RenderFieldsStory>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const SectionField: Story = {};

export const TransparentSectionField: Story = {
  args: { sectionVariant: "transparent" },
  play: async ({ canvasElement }) => {
    const section = within(canvasElement).getByRole("group", { name: /record details/i });
    const separator = within(section).getByRole("separator");
    const heading = within(section).getByRole("heading");
    await expect(section).toHaveClass("bg-transparent", "ring-0");
    await expect(separator).toBeVisible();
    await expect(Math.round(separator.getBoundingClientRect().top - heading.getBoundingClientRect().bottom)).toBe(4);
    await expect(Math.round(separator.getBoundingClientRect().left)).toBe(
      Math.round(heading.getBoundingClientRect().left),
    );
    await expect(Math.round(separator.getBoundingClientRect().width)).toBe(
      Math.round(heading.getBoundingClientRect().width),
    );
  },
};

export const RowField: Story = {};

// PROTOTYPE: Field-presentation variants, selected as separate Storybook stories.
export const PrototypeCompactGrid: Story = {
  args: { presentation: "compact" },
};

export const PrototypeSplitPane: Story = {
  args: { presentation: "split" },
};

export const PrototypeProgressiveDisclosure: Story = {
  args: { presentation: "progressive" },
};

export const PrototypeSettingsRows: Story = {
  args: { presentation: "settings" },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const heading = canvas.getByRole("heading", { name: "Title" });
    const control = canvas.getByRole("textbox", { name: "Title" });
    await expect(heading.getBoundingClientRect().right).toBeLessThan(control.getBoundingClientRect().left);
  },
};

export const PrototypeAlignedLabels: Story = {
  args: { presentation: "aligned" },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const heading = canvas.getByRole("heading", { name: "Title" });
    const control = canvas.getByRole("textbox", { name: "Title" });
    await expect(heading.getBoundingClientRect().right).toBeLessThan(control.getBoundingClientRect().left);
  },
};

export const PrototypePromptCards: Story = {
  args: { presentation: "prompt-cards" },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const description = canvas.getByText("The name that identifies this record in lists and search results.");
    const control = canvas.getByRole("textbox", { name: "Title" });
    await expect(description.getBoundingClientRect().bottom).toBeLessThan(control.getBoundingClientRect().top);
  },
};

export const Disabled: Story = {
  args: { disabled: true },
};

export const InventoryItemRelationship: Story = {
  render: () => <InventoryRelationshipStory />,
  play: async ({ canvasElement }) => {
    await userEvent.click(within(canvasElement).getByRole("combobox", { name: "Inventory item" }));
    const option = within(canvasElement.ownerDocument.body).getByRole("option", {
      name: "Zeiss LSM 900 confocal",
    });
    const item = option.querySelector<HTMLElement>('[data-slot="item"]');
    if (!item) throw new Error("Expected the inventory option to render an Item");
    await expect(getComputedStyle(item).padding).toBe("0px");
    await userEvent.keyboard("{Escape}");
  },
};
