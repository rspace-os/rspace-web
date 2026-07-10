import { within } from "@testing-library/react";

export function getIndexOfTableCell(tablerow: HTMLElement, name: string | RegExp): number {
  const cell = within(tablerow).getByRole("columnheader", { name });
  return within(tablerow).getAllByRole("columnheader").indexOf(cell);
}

export async function findTableCell(
  table: HTMLElement,
  { columnHeading, rowIndex }: { columnHeading: string; rowIndex: number },
): Promise<HTMLElement> {
  const headingRow = (await within(table).findAllByRole("row"))[0];
  if (!headingRow) throw new Error("Table doesn't have a header row.");

  const matchingColumnHeaders = await within(headingRow).findAllByRole("columnheader", { name: columnHeading });
  if (!matchingColumnHeaders.length) throw new Error(`There are no columns with the heading "${columnHeading}".`);
  if (matchingColumnHeaders.length > 1)
    throw new Error(`There is more than 1 column with the heading "${columnHeading}".`);

  const chosenRow = within(table).getAllByRole("row")[rowIndex + 1];
  let cells = within(chosenRow).queryAllByRole("cell");
  if (cells.length === 0) cells = within(chosenRow).queryAllByRole("gridcell");
  return cells[getIndexOfTableCell(headingRow, columnHeading)];
}
