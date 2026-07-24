import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, userEvent, within } from "storybook/test";
import {
  Combobox,
  ComboboxChip,
  ComboboxChips,
  ComboboxChipsInput,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxGroup,
  ComboboxInput,
  ComboboxItem,
  ComboboxLabel,
  ComboboxList,
  ComboboxSeparator,
  ComboboxValue,
  useComboboxAnchor,
} from "./combobox";

const fruits = ["Apple", "Banana", "Cherry", "Date", "Elderberry", "Fig", "Grape"];

const meta = {
  title: "DesignSystem/Combobox",
  component: Combobox,
  tags: ["autodocs"],
} satisfies Meta<typeof Combobox>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => (
    <Combobox items={fruits}>
      <ComboboxInput placeholder="Search fruit..." />
      <ComboboxContent>
        <ComboboxList>
          {(item: string) => <ComboboxItem key={item} value={item}>{item}</ComboboxItem>}
        </ComboboxList>
        <ComboboxEmpty>No fruit found.</ComboboxEmpty>
      </ComboboxContent>
    </Combobox>
  ),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const input = canvas.getByPlaceholderText("Search fruit...");
    expect(input).toBeInTheDocument();
    await userEvent.click(input);
    const body = within(canvasElement.ownerDocument.body);
    expect(await body.findByText("Apple")).toBeInTheDocument();
  },
};

export const Grouped: Story = {
  render: () => (
    <Combobox>
      <ComboboxInput placeholder="Search produce..." />
      <ComboboxContent>
        <ComboboxList>
          <ComboboxGroup>
            <ComboboxLabel>Fruits</ComboboxLabel>
            <ComboboxItem value="apple">Apple</ComboboxItem>
            <ComboboxItem value="banana">Banana</ComboboxItem>
            <ComboboxItem value="cherry">Cherry</ComboboxItem>
          </ComboboxGroup>
          <ComboboxSeparator />
          <ComboboxGroup>
            <ComboboxLabel>Vegetables</ComboboxLabel>
            <ComboboxItem value="carrot">Carrot</ComboboxItem>
            <ComboboxItem value="potato">Potato</ComboboxItem>
          </ComboboxGroup>
        </ComboboxList>
      </ComboboxContent>
    </Combobox>
  ),
};

function MultipleExample() {
  const anchor = useComboboxAnchor();
  return (
    <Combobox items={fruits} multiple>
      <ComboboxChips ref={anchor}>
        <ComboboxValue>
          {(value: string[]) =>
            value.map((item) => <ComboboxChip key={item}>{item}</ComboboxChip>)
          }
        </ComboboxValue>
        <ComboboxChipsInput placeholder="Add fruit..." />
      </ComboboxChips>
      <ComboboxContent anchor={anchor}>
        <ComboboxList>
          {(item: string) => <ComboboxItem key={item} value={item}>{item}</ComboboxItem>}
        </ComboboxList>
        <ComboboxEmpty>No fruit found.</ComboboxEmpty>
      </ComboboxContent>
    </Combobox>
  );
}

export const Multiple: Story = {
  render: () => <MultipleExample />,
};
