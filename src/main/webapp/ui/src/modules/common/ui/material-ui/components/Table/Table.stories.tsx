/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Table/Table.stories.tsx
 * Upstream project license: MIT
 */
import Paper from "@mui/material/Paper";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { createBooleanArgType, createSelectArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Data Display/Table",
  component: Table,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    size: createSelectArgType(["small", "medium"], "medium", "The size of the table.", "Appearance"),
    padding: createSelectArgType(["checkbox", "none", "normal"], "normal", "The padding of the cells.", "Layout"),
    stickyHeader: createBooleanArgType("Set the header sticky.", false, "Layout"),
    children: { control: false },
  },
} satisfies Meta<typeof Table>;

export default meta;
type Story = StoryObj<typeof meta>;

interface TableData {
  name: string;
  calories: number;
  fat: number;
  carbs: number;
  protein: number;
}

const createData = (name: string, calories: number, fat: number, carbs: number, protein: number): TableData => {
  return { name, calories, fat, carbs, protein };
};

const rows: TableData[] = [
  createData("Frozen yoghurt", 159, 6.0, 24, 4.0),
  createData("Ice cream sandwich", 237, 9.0, 37, 4.3),
  createData("Eclair", 262, 16.0, 24, 6.0),
  createData("Cupcake", 305, 3.7, 67, 4.3),
  createData("Gingerbread", 356, 16.0, 49, 3.9),
];

export const BasicTable: Story = {
  render: (args) => (
    <TableContainer component={Paper} sx={{ maxHeight: 240 }} tabIndex={0}>
      <Table {...args} sx={{ minWidth: 650 }} aria-label="simple table">
        <TableHead>
          <TableRow>
            <TableCell>Dessert (100g serving)</TableCell>
            <TableCell align="right">Calories</TableCell>
            <TableCell align="right">Fat&nbsp;(g)</TableCell>
            <TableCell align="right">Carbs&nbsp;(g)</TableCell>
            <TableCell align="right">Protein&nbsp;(g)</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.name} sx={{ "&:last-child td, &:last-child th": { border: 0 } }}>
              <TableCell component="th" scope="row">
                {row.name}
              </TableCell>
              <TableCell align="right">{row.calories}</TableCell>
              <TableCell align="right">{row.fat}</TableCell>
              <TableCell align="right">{row.carbs}</TableCell>
              <TableCell align="right">{row.protein}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  ),
};

export const DenseTable: Story = {
  render: (args) => (
    <TableContainer component={Paper} sx={{ maxHeight: 240 }} tabIndex={0}>
      <Table {...args} sx={{ minWidth: 650 }} size="small" aria-label="a dense table">
        <TableHead>
          <TableRow>
            <TableCell>Dessert (100g serving)</TableCell>
            <TableCell align="right">Calories</TableCell>
            <TableCell align="right">Fat&nbsp;(g)</TableCell>
            <TableCell align="right">Carbs&nbsp;(g)</TableCell>
            <TableCell align="right">Protein&nbsp;(g)</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.name} sx={{ "&:last-child td, &:last-child th": { border: 0 } }}>
              <TableCell component="th" scope="row">
                {row.name}
              </TableCell>
              <TableCell align="right">{row.calories}</TableCell>
              <TableCell align="right">{row.fat}</TableCell>
              <TableCell align="right">{row.carbs}</TableCell>
              <TableCell align="right">{row.protein}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  ),
};

export const InteractionTest: Story = {
  render: (args) => (
    <TableContainer component={Paper} sx={{ maxHeight: 240 }} tabIndex={0}>
      <Table {...args} aria-label="test table">
        <TableHead>
          <TableRow>
            <TableCell>Name</TableCell>
            <TableCell align="right">Calories</TableCell>
            <TableCell align="right">Fat (g)</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          <TableRow>
            <TableCell component="th" scope="row">
              Frozen yoghurt
            </TableCell>
            <TableCell align="right">159</TableCell>
            <TableCell align="right">6.0</TableCell>
          </TableRow>
          <TableRow>
            <TableCell component="th" scope="row">
              Ice cream sandwich
            </TableCell>
            <TableCell align="right">237</TableCell>
            <TableCell align="right">9.0</TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </TableContainer>
  ),
  play: async ({ canvasElement, step }) => {
    const canvas = within(canvasElement);

    await step("Verify table renders", async () => {
      const table = canvas.getByRole("table", { name: "test table" });
      await expect(table).toBeInTheDocument();
    });

    await step("Verify table headers", async () => {
      const nameHeader = canvas.getByRole("columnheader", { name: "Name" });
      const caloriesHeader = canvas.getByRole("columnheader", {
        name: "Calories",
      });
      const fatHeader = canvas.getByRole("columnheader", { name: "Fat (g)" });
      await expect(nameHeader).toBeInTheDocument();
      await expect(caloriesHeader).toBeInTheDocument();
      await expect(fatHeader).toBeInTheDocument();
    });

    await step("Verify table rows", async () => {
      const rows = canvas.getAllByRole("row");
      // 1 header row + 2 body rows = 3 total
      await expect(rows).toHaveLength(3);
    });

    await step("Verify table cell content", async () => {
      const yoghurtCell = canvas.getByRole("rowheader", {
        name: "Frozen yoghurt",
      });
      const iceCreamCell = canvas.getByRole("rowheader", {
        name: "Ice cream sandwich",
      });
      await expect(yoghurtCell).toBeInTheDocument();
      await expect(iceCreamCell).toBeInTheDocument();
    });

    await step("Test accessibility", async () => {
      const table = canvas.getByRole("table");
      await expect(table).toHaveAttribute("aria-label", "test table");
    });
  },
};
