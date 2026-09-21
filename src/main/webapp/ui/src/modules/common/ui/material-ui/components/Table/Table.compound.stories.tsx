import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableFooter from "@mui/material/TableFooter";
import TableHead from "@mui/material/TableHead";
import TablePagination from "@mui/material/TablePagination";
import TablePaginationActions from "@mui/material/TablePaginationActions";
import TableRow from "@mui/material/TableRow";
import TableSortLabel from "@mui/material/TableSortLabel";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { expect, userEvent, within } from "storybook/test";

const meta = {
  title: "Material UI/Data Display/Table/Composition",
  component: Table,
  parameters: { a11y: { test: "error" } },
  argTypes: { size: { control: "select", options: ["small", "medium"] } },
} satisfies Meta<typeof Table>;
export default meta;
type Story = StoryObj<typeof meta>;

/** MUI Table supplies context to its cells. Sorting and page-size changes reset the page; pagination actions stay in the footer. */
export const Composed: Story = {
  args: { size: "medium" },
  render: function SampleTable(args) {
    const [page, setPage] = useState(0);
    const [rowsPerPage, setRowsPerPage] = useState(2);
    const [order, setOrder] = useState<"asc" | "desc">("asc");
    const samples = ["Sample A", "Sample B", "Sample C", "Sample D"].sort((a, b) =>
      order === "asc" ? a.localeCompare(b) : b.localeCompare(a),
    );
    return (
      <TableContainer>
        <Table {...args} aria-label="Samples">
          <TableHead>
            <TableRow>
              <TableCell sortDirection={order}>
                <TableSortLabel
                  active
                  direction={order}
                  onClick={() => {
                    setOrder(order === "asc" ? "desc" : "asc");
                    setPage(0);
                  }}
                >
                  Name
                </TableSortLabel>
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {samples.slice(page * rowsPerPage, (page + 1) * rowsPerPage).map((name) => (
              <TableRow key={name}>
                <TableCell>{name}</TableCell>
              </TableRow>
            ))}
          </TableBody>
          <TableFooter>
            <TableRow>
              <TablePagination
                count={samples.length}
                page={page}
                rowsPerPage={rowsPerPage}
                rowsPerPageOptions={[2, 4]}
                onPageChange={(_, next) => setPage(next)}
                onRowsPerPageChange={(event) => {
                  setRowsPerPage(Number(event.target.value));
                  setPage(0);
                }}
                ActionsComponent={TablePaginationActions}
                showFirstButton
                showLastButton
              />
            </TableRow>
          </TableFooter>
        </Table>
      </TableContainer>
    );
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await expect(canvas.getByRole("cell", { name: "Sample A" })).toBeVisible();
    await userEvent.click(canvas.getByRole("button", { name: "Go to next page" }));
    await expect(canvas.getByRole("cell", { name: "Sample C" })).toBeVisible();
    await expect(canvas.queryByRole("cell", { name: "Sample A" })).not.toBeInTheDocument();
    await userEvent.click(canvas.getByRole("button", { name: /Name/ }));
    await expect(canvas.getByRole("columnheader")).toHaveAttribute("aria-sort", "descending");
    await expect(canvas.getAllByRole("cell")[0]).toHaveTextContent("Sample D");
    await userEvent.click(canvas.getByRole("combobox", { name: "Rows per page:" }));
    const page = within(canvasElement.ownerDocument.body);
    await userEvent.click(await page.findByRole("option", { name: "4" }));
    await expect(canvas.getByRole("cell", { name: "Sample A" })).toBeVisible();
    await expect(canvas.getByRole("button", { name: "Go to next page" })).toBeDisabled();
  },
};
