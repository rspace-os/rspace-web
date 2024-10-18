//@flow strict
/* eslint-env jest */
/*
 * This module is for defining custom queries as part of our unit testing
 * toolchain. For documentation on custom queries, see these links
 * https://testing-library.com/docs/dom-testing-library/api-custom-queries/
 * https://testing-library.com/docs/react-testing-library/setup#add-custom-queries
 */
import {
  within,
  render,
  queries,
  type Queries,
  type Element,
} from "@testing-library/react";

export function getIndexOfTableCell(
  tablerow: Element,
  name: string | RegExp
): number {
  const cell = within(tablerow).getByRole("columnheader", { name });
  return within(tablerow)
    .getAllByRole("columnheader")
    .findIndex((c) => c === cell);
}

/**
 * This custom query gets a particular cell of a provided table.
 *
 * This query abstracts away the steps to `findAllByRole` tablerow and cell and
 * the indexing into those arrays, whilst doing so in a way that should any
 * part of the query fail, such as because of out-of-bounds-indexing, then the
 * whole query fails.
 *
 * @param body This is automatically inserted by the test runtime and should
 *             not be specified by a caller. It a reference to the root node of
 *             the DOM.
 * @param table This a reference to the table in question, which should be
 *              selected using standard queries: probably `getRoleBy('table')`.
 * @param The third argument is an object which specifies which cell in the
 *        table to select. This is an object so that this function is flexible
 *        to be adapted to various ways of specifying the cell (by name of the
 *        heading, numerical index, etc) in both axes. Being an object it also
 *        makes calls to this query more explicit by avoid magic numbers;
 *        `findTableCell(table, { columnHeading: "foo", rowIndex: 0 })` is just
 *        a lot clearer than `findTableCell(table, "foo", 0)`.
 *
 * @returns Just like other `findBy...` queries, this query returns a promise
 *          that resolves with a table cell when one is found, rejecting if
 *          none that matches is found.
 *
 * @example
 * // import { render, within } from "$thisFile"; instead of "@testing-library/react";
 * render(
 *   <table>
 *     <thead>
 *      <tr>
 *        <th>Foo</th>
 *        <th>Bar</th>
 *      </tr>
 *     </thead>
 *     <tbody>
 *      <tr>
 *        <td>One</td>
 *        <td>Two</td>
 *      </tr>
 *      <tr>
 *        <td>Three</td>
 *        <td>Four</td>
 *      </tr>
 *     </tbody>
 *   </table>
 * );
 * expect(
 *   await within(screen.getByRole("table")).findTableCell({
 *     columnHeading: "Foo",
 *     rowIndex: 0,
 *   })
 * ).toHaveTextContent("One");
 * expect(
 *   await within(screen.getByRole("table")).findTableCell({
 *     columnHeading: "Bar",
 *     rowIndex: 1,
 *   })
 * ).toHaveTextContent("Four");
 */
async function findTableCell(
  table: Element,
  { columnHeading, rowIndex }: {| columnHeading: string, rowIndex: number |}
): Promise<Element> {
  const headingRow = (await within(table).findAllByRole("row"))[0];
  if (!headingRow) throw new Error("Table doesn't have a header row.");

  const matchingColumnHeaders = await within(headingRow).findAllByRole(
    "columnheader",
    { name: columnHeading }
  );
  if (!matchingColumnHeaders.length)
    throw new Error(
      `There are no columns with the heading "${columnHeading}".`
    );
  if (matchingColumnHeaders.length > 1)
    throw new Error(
      `There is more than 1 column with the heading "${columnHeading}".`
    );

  const indexOfColumnHeading = getIndexOfTableCell(headingRow, columnHeading);

  const chosenRow = within(table).getAllByRole("row")[rowIndex + 1];

  let cells = within(chosenRow).queryAllByRole("cell");
  if (cells.length === 0) cells = within(chosenRow).queryAllByRole("gridcell");
  return cells[indexOfColumnHeading];
}

const allQueries = { ...queries, findTableCell, getIndexOfTableCell };
const customRender: typeof render = (ui, options) =>
  render(ui, { queries: { ...queries, findTableCell }, ...options });
const customWithin = (
  element: Element,
  moreQueries: ?{ ... }
): { ...Queries, findTableCell: ({| columnHeading: string, rowIndex: number |}) => Promise<Element>, getIndexOfTableCell: (string | RegExp) => number, ...  } => within(element, { ...allQueries, ...moreQueries });

// re-export everything
export * from "@testing-library/react";
// override render method
export { customRender as render, customWithin as within };
