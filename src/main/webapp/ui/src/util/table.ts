import { mapObject, match } from "./Util";
import { type Order } from "./types";
import { type GridColDef } from "@mui/x-data-grid";

export function desc<T extends string, U>(
  a: { [K in T]: U },
  b: { [K in T]: U },
  orderBy: T
): -1 | 0 | 1 {
  if (b[orderBy] < a[orderBy]) {
    return -1;
  }
  if (b[orderBy] > a[orderBy]) {
    return 1;
  }
  return 0;
}

/*
 * Returns a copy of the given array, sorted according to the given compare
 * function, keeping the original order of elements where the compare function
 * returns 0.
 *
 * In all modern browsers, (since 2019) this behaviour is guaranteed
 * but not so for older browsers. For more information, see
 * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/sort#sort_stability
 */
export function stableSort<T>(
  array: ReadonlyArray<T>,
  cmp: (t1: T, t2: T) => -1 | 0 | 1
): Array<T> {
  const stabilizedThis: Array<[T, number]> = array.map((el, index) => [
    el,
    index,
  ]);
  stabilizedThis.sort((a, b) => {
    const order = cmp(a[0], b[0]);
    if (order !== 0) return order;
    return a[1] - b[1];
  });
  return stabilizedThis.map((el) => el[0]);
}

export function getSorting<T extends string, U>(
  order: Order,
  orderBy: T
): (a: { [K in T]: U }, b: { [K in T]: U }) => -1 | 0 | 1 {
  return (
    order === "desc"
      ? (a, b) => desc(a, b, orderBy)
      : (a, b) => -desc(a, b, orderBy)
  ) as (a: { [K in T]: U }, b: { [K in T]: U }) => -1 | 0 | 1;
}

const transformObject = <T, U extends keyof T, V>(
  obj: T,
  map: { [K in U]: (t: T) => V }
): { [K in U]: V } => mapObject((k, v) => v(obj), map);

export const getSortingSpecified =
  <T extends { [k: string]: string }>(
    order: Order,
    orderBy: string,
    map: { [K in keyof T]: (t: T) => string }
  ): ((a: T, b: T) => -1 | 0 | 1) =>
  (a: T, b: T) =>
    (desc(transformObject(a, map), transformObject(b, map), orderBy) *
      (order === "desc" ? 1 : -1)) as -1 | 0 | 1;

/**
 * Returns a value for the `pageSizeOptions` prop of pagination controls; the
 * list of various page sizes that we support. Any sizes from the standard list
 * that are larger than the total number of results are discarded. If the total
 * length is less than 100 (the largest standard page size) or the `all`
 * override is set, then a further option of all the available rows is also
 * included.
 */
export const paginationOptions = (
  resultsLength: number
): Array<number | { value: number; label: string }> => [
  ...[5, 10, 25, 100].filter((p) => p < resultsLength),
  ...(resultsLength <= 100
    ? [{ value: resultsLength, label: `${resultsLength} (All)` }]
    : []),
];

export const DataGridColumn = {
  /**
   * Define a new column where the cell's value is simply the property of the
   * object `Row` with key `Field`.
   */
  newColumnWithFieldName<
    Field extends string,
    Row extends object & { [K in Field]: unknown }
  >(
    // Also acts as the unique identifier for the column
    field: Field,
    rest: Omit<GridColDef<Row>, "field">
  ): GridColDef<Row> {
    return {
      field,
      ...rest,
    };
  },

  /**
   * Define a new column where the cell's value is the output of the
   * `mapFunction` function when called with the value of the `Field` field.
   *
   * This allows for simple transformations to the data such as formatting a
   * date in the user's locale.
   */
  newColumnWithValueMapper<
    Field extends string,
    Row extends object & { [K in Field]: unknown }
  >(
    // The name of field to be transformed
    field: Field,
    // Function that does the transformation
    mapFunction: (value: Row[Field]) => string,
    rest: Omit<GridColDef<Row>, "field" | "valueGetter">
  ): GridColDef<Row> {
    return {
      field,
      valueGetter: (valueBefore: Row[Field]) => mapFunction(valueBefore),
      ...rest,
    };
  },

  /**
   * Define a new column where the cell's value is the output of the
   * `valueGetter` function.
   */
  newColumnWithValueGetter<
    Field extends string,
    Row extends object & { [K in Field]: unknown }
  >(
    // Unique identifier for the column
    field: Field,
    // Function for getting a cell's value from a Row
    valueGetter: (row: Row) => string,
    rest: Omit<GridColDef<Row>, "field" | "valueGetter">
  ): GridColDef<Row> {
    return {
      field,
      valueGetter: (_ignore: unknown, row: Row) => valueGetter(row),
      ...rest,
    };
  },
};
