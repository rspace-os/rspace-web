/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Autocomplete/Autocomplete.stories.tsx
 * Upstream project license: MIT
 */
import Autocomplete from "@mui/material/Autocomplete";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import type { Meta, StoryObj } from "@storybook/react-vite";
import React from "react";
import { expect, fn, userEvent, within } from "storybook/test";
import { createBooleanArgType, muiDisabledArgType } from "../../argTypeTemplates";

// Top 100 films as rated by IMDb users. http://www.imdb.com/chart/top
const top100Films = [
  { label: "The Shawshank Redemption", year: 1994 },
  { label: "The Godfather", year: 1972 },
  { label: "The Godfather: Part II", year: 1974 },
  { label: "The Dark Knight", year: 2008 },
  { label: "12 Angry Men", year: 1957 },
  { label: "Schindler's List", year: 1993 },
  { label: "Pulp Fiction", year: 1994 },
  {
    label: "The Lord of the Rings: The Return of the King",
    year: 2003,
  },
  { label: "The Good, the Bad and the Ugly", year: 1966 },
  { label: "Fight Club", year: 1999 },
  {
    label: "The Lord of the Rings: The Fellowship of the Ring",
    year: 2001,
  },
  {
    label: "Star Wars: Episode V - The Empire Strikes Back",
    year: 1980,
  },
  { label: "Forrest Gump", year: 1994 },
  { label: "Inception", year: 2010 },
  {
    label: "The Lord of the Rings: The Two Towers",
    year: 2002,
  },
  { label: "One Flew Over the Cuckoo's Nest", year: 1975 },
  { label: "Goodfellas", year: 1990 },
  { label: "The Matrix", year: 1999 },
  { label: "Seven Samurai", year: 1954 },
  {
    label: "Star Wars: Episode IV - A New Hope",
    year: 1977,
  },
  { label: "City of God", year: 2002 },
  { label: "Se7en", year: 1995 },
  { label: "The Silence of the Lambs", year: 1991 },
  { label: "It's a Wonderful Life", year: 1946 },
  { label: "Life Is Beautiful", year: 1997 },
  { label: "The Usual Suspects", year: 1995 },
  { label: "Léon: The Professional", year: 1994 },
  { label: "Spirited Away", year: 2001 },
  { label: "Saving Private Ryan", year: 1998 },
  { label: "Once Upon a Time in the West", year: 1968 },
  { label: "American History X", year: 1998 },
  { label: "Interstellar", year: 2014 },
  { label: "Casablanca", year: 1942 },
  { label: "City Lights", year: 1931 },
  { label: "Psycho", year: 1960 },
  { label: "The Green Mile", year: 1999 },
  { label: "The Intouchables", year: 2011 },
  { label: "Modern Times", year: 1936 },
  { label: "Raiders of the Lost Ark", year: 1981 },
  { label: "Rear Window", year: 1954 },
  { label: "The Pianist", year: 2002 },
  { label: "The Departed", year: 2006 },
  { label: "Terminator 2: Judgment Day", year: 1991 },
  { label: "Back to the Future", year: 1985 },
  { label: "Whiplash", year: 2014 },
  { label: "Gladiator", year: 2000 },
  { label: "Memento", year: 2000 },
  { label: "The Prestige", year: 2006 },
  { label: "The Lion King", year: 1994 },
  { label: "Apocalypse Now", year: 1979 },
  { label: "Alien", year: 1979 },
  { label: "Sunset Boulevard", year: 1950 },
  {
    label: "Dr. Strangelove or: How I Learned to Stop Worrying and Love the Bomb",
    year: 1964,
  },
  { label: "The Great Dictator", year: 1940 },
  { label: "Cinema Paradiso", year: 1988 },
  { label: "The Lives of Others", year: 2006 },
  { label: "Grave of the Fireflies", year: 1988 },
  { label: "Paths of Glory", year: 1957 },
  { label: "Django Unchained", year: 2012 },
  { label: "The Shining", year: 1980 },
  { label: "WALL·E", year: 2008 },
  { label: "American Beauty", year: 1999 },
  { label: "The Dark Knight Rises", year: 2012 },
  { label: "Princess Mononoke", year: 1997 },
  { label: "Aliens", year: 1986 },
  { label: "Oldboy", year: 2003 },
  { label: "Once Upon a Time in America", year: 1984 },
  { label: "Witness for the Prosecution", year: 1957 },
  { label: "Das Boot", year: 1981 },
  { label: "Citizen Kane", year: 1941 },
  { label: "North by Northwest", year: 1959 },
  { label: "Vertigo", year: 1958 },
  {
    label: "Star Wars: Episode VI - Return of the Jedi",
    year: 1983,
  },
  { label: "Reservoir Dogs", year: 1992 },
  { label: "Braveheart", year: 1995 },
  { label: "M", year: 1931 },
  { label: "Requiem for a Dream", year: 2000 },
  { label: "Amélie", year: 2001 },
  { label: "A Clockwork Orange", year: 1971 },
  { label: "Like Stars on Earth", year: 2007 },
  { label: "Taxi Driver", year: 1976 },
  { label: "Lawrence of Arabia", year: 1962 },
  { label: "Double Indemnity", year: 1944 },
  {
    label: "Eternal Sunshine of the Spotless Mind",
    year: 2004,
  },
  { label: "Amadeus", year: 1984 },
  { label: "To Kill a Mockingbird", year: 1962 },
  { label: "Toy Story 3", year: 2010 },
  { label: "Logan", year: 2017 },
  { label: "Full Metal Jacket", year: 1987 },
  { label: "Dangal", year: 2016 },
  { label: "The Sting", year: 1973 },
  { label: "2001: A Space Odyssey", year: 1968 },
  { label: "Singin' in the Rain", year: 1952 },
  { label: "Toy Story", year: 1995 },
  { label: "Bicycle Thieves", year: 1948 },
  { label: "The Kid", year: 1921 },
  { label: "Inglourious Basterds", year: 2009 },
  { label: "Snatch", year: 2000 },
  { label: "3 Idiots", year: 2009 },
  { label: "Monty Python and the Holy Grail", year: 1975 },
];

interface FilmOptionType {
  label: string;
  year: number;
}

const meta = {
  title: "Material UI/Inputs/Autocomplete",
  component: Autocomplete<FilmOptionType, false, false, false>,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    disabled: muiDisabledArgType,
    disableClearable: createBooleanArgType("If true, the input cannot be cleared.", false, "State"),
    disableCloseOnSelect: createBooleanArgType(
      "If true, the popup will not close when a value is selected.",
      false,
      "Behavior",
    ),
    disablePortal: createBooleanArgType(
      "If true, the Popper content will be under the DOM hierarchy of the parent component.",
      false,
      "Layout",
    ),
    freeSolo: createBooleanArgType(
      "If true, the Autocomplete is free solo, meaning that the user input is not bound to provided options.",
      false,
      "Content",
    ),
    fullWidth: createBooleanArgType(
      "If true, the input will take up the full width of its container.",
      false,
      "Layout",
    ),
    loading: createBooleanArgType("If true, the component is in a loading state.", false, "State"),
    multiple: createBooleanArgType(
      "If true, value must be an array and the menu will support multiple selections.",
      false,
      "State",
    ),
    openOnFocus: createBooleanArgType("If true, the popup will open on input focus.", false, "Behavior"),
    readOnly: createBooleanArgType("If true, the component becomes read-only.", false, "State"),
    size: {
      control: "radio",
      options: ["small", "medium"],
      description: "The size of the component.",
      table: {
        defaultValue: { summary: "medium" },
        category: "Appearance",
        type: { summary: '"small" | "medium"' },
      },
    },
    // Disable complex props that require functions/JSX
    options: { control: false },
    renderInput: { control: false },
    getOptionLabel: { control: false },
  },
} satisfies Meta<typeof Autocomplete<FilmOptionType, false, false, false>>;

export default meta;
type Story = StoryObj<typeof meta>;

export const ComboBox: Story = {
  args: {} as never,
  render: () => (
    <Autocomplete
      disablePortal
      id="combo-box-demo"
      options={top100Films}
      sx={{ width: 300 }}
      renderInput={(params) => <TextField {...params} label="Movie" />}
    />
  ),
};

export const Playground: Story = {
  args: { options: top100Films, renderInput: (params) => <TextField {...params} label="Movie" />, size: "medium" },
  // Changing selection mode resets the uncontrolled value to the appropriate shape.
  render: (args) => <Autocomplete key={String(Boolean(args.multiple))} {...args} sx={{ width: 300 }} />,
};

export function Behaviors() {
  const defaultProps = {
    options: top100Films,
    getOptionLabel: (option: FilmOptionType) => option.label,
  };
  const flatProps = {
    options: top100Films.map((option) => option.label),
  };
  const [value, setValue] = React.useState<FilmOptionType | null>(null);

  return (
    <Stack spacing={1} sx={{ width: 300 }}>
      <Autocomplete
        {...defaultProps}
        id="disable-close-on-select"
        disableCloseOnSelect
        renderInput={(params) => <TextField {...params} label="disableCloseOnSelect" variant="standard" />}
      />
      <Autocomplete
        {...defaultProps}
        id="clear-on-escape"
        clearOnEscape
        renderInput={(params) => <TextField {...params} label="clearOnEscape" variant="standard" />}
      />
      <Autocomplete
        {...defaultProps}
        id="disable-clearable"
        disableClearable
        renderInput={(params) => <TextField {...params} label="disableClearable" variant="standard" />}
      />
      <Autocomplete
        {...defaultProps}
        id="include-input-in-list"
        includeInputInList
        renderInput={(params) => <TextField {...params} label="includeInputInList" variant="standard" />}
      />
      <Autocomplete
        {...flatProps}
        id="flat-demo"
        renderInput={(params) => <TextField {...params} label="flat" variant="standard" />}
      />
      <Autocomplete
        {...defaultProps}
        id="controlled-demo"
        value={value}
        onChange={(_event: unknown, newValue: FilmOptionType | null) => {
          setValue(newValue);
        }}
        renderInput={(params) => <TextField {...params} label="controlled" variant="standard" />}
      />
      <Autocomplete
        {...defaultProps}
        id="auto-complete"
        autoComplete
        includeInputInList
        renderInput={(params) => <TextField {...params} label="autoComplete" variant="standard" />}
      />
      <Autocomplete
        {...defaultProps}
        id="disable-list-wrap"
        disableListWrap
        renderInput={(params) => <TextField {...params} label="disableListWrap" variant="standard" />}
      />
      <Autocomplete
        {...defaultProps}
        id="open-on-focus"
        openOnFocus
        renderInput={(params) => <TextField {...params} label="openOnFocus" variant="standard" />}
      />
      <Autocomplete
        {...defaultProps}
        id="auto-highlight"
        autoHighlight
        renderInput={(params) => <TextField {...params} label="autoHighlight" variant="standard" />}
      />
      <Autocomplete
        {...defaultProps}
        id="auto-select"
        autoSelect
        renderInput={(params) => <TextField {...params} label="autoSelect" variant="standard" />}
      />
      <Autocomplete
        {...defaultProps}
        id="disabled"
        disabled
        renderInput={(params) => <TextField {...params} label="disabled" variant="standard" />}
      />
      <Autocomplete
        {...defaultProps}
        id="disable-portal"
        disablePortal
        renderInput={(params) => <TextField {...params} label="disablePortal" variant="standard" />}
      />
      <Autocomplete
        {...defaultProps}
        id="blur-on-select"
        blurOnSelect
        renderInput={(params) => <TextField {...params} label="blurOnSelect" variant="standard" />}
      />
      <Autocomplete
        {...defaultProps}
        id="clear-on-blur"
        clearOnBlur
        renderInput={(params) => <TextField {...params} label="clearOnBlur" variant="standard" />}
      />
      <Autocomplete
        {...defaultProps}
        id="select-on-focus"
        selectOnFocus
        renderInput={(params) => <TextField {...params} label="selectOnFocus" variant="standard" />}
      />
      <Autocomplete
        {...flatProps}
        id="readOnly"
        readOnly
        defaultValue={flatProps.options[13]}
        renderInput={(params) => <TextField {...params} label="readOnly" variant="standard" />}
      />
    </Stack>
  );
}

export const InteractionTest: Story = {
  args: {} as never,
  render: () => {
    const [value, setValue] = React.useState<FilmOptionType | null>(null);
    const handleChange = fn((_event: unknown, newValue: FilmOptionType | null) => {
      setValue(newValue);
    });

    return (
      <Autocomplete
        disablePortal
        id="autocomplete-test"
        options={top100Films.slice(0, 10)}
        value={value}
        onChange={handleChange}
        sx={{ width: 300 }}
        renderInput={(params) => <TextField {...params} label="Movie" />}
      />
    );
  },
  play: async ({ canvasElement, step }) => {
    const canvas = within(canvasElement);

    await step("Verify initial render", async () => {
      const input = canvas.getByRole("combobox", { name: /movie/i });
      await expect(input).toBeInTheDocument();
      await expect(input).toHaveValue("");
    });

    await step("Click input to open dropdown", async () => {
      const input = canvas.getByRole("combobox", { name: /movie/i });
      await userEvent.click(input);

      const listbox = canvas.getByRole("listbox");
      await expect(listbox).toBeInTheDocument();
    });

    await step("Type to filter options", async () => {
      const input = canvas.getByRole("combobox", { name: /movie/i });
      await userEvent.clear(input);
      await userEvent.type(input, "Godfather");

      const options = canvas.getAllByRole("option");
      await expect(options.length).toBeGreaterThan(0);
      // Use exact match to avoid matching "The Godfather: Part II"
      await expect(canvas.getByRole("option", { name: "The Godfather" })).toBeInTheDocument();
    });

    await step("Select an option from dropdown", async () => {
      const option = canvas.getByRole("option", { name: /^The Godfather$/i });
      await userEvent.click(option);

      const input = canvas.getByRole("combobox", { name: /movie/i });
      await expect(input).toHaveValue("The Godfather");
    });

    await step("Clear selection", async () => {
      const clearButton = canvas.getByRole("button", { name: /clear/i });
      await userEvent.click(clearButton);

      const input = canvas.getByRole("combobox", { name: /movie/i });
      await expect(input).toHaveValue("");
    });
  },
};
