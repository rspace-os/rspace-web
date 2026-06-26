import type { GridColDef } from "@mui/x-data-grid";
import { mapValues } from "es-toolkit";
import type { Order } from "./types";

export function desc<Row>(a: Row, b: Row, orderBy: string): -1 | 0 | 1 {
  // `orderBy` is a runtime string key, so reading it off a generic row is an
  // inherently dynamic access. Sort values are strings or numbers, both of
  // which order correctly under `<`/`>` at runtime; the numeric view here only
  // satisfies the type checker and keeps the public signature row-generic.
  const av = (a as Record<string, number>)[orderBy];
  const bv = (b as Record<string, number>)[orderBy];
  if (bv < av) {
    return -1;
  }
  if (bv > av) {
    return 1;
  }
  return 0;
}

export function getSorting<Row>(order: Order, orderBy: string): (a: Row, b: Row) => -1 | 0 | 1 {
  return order === "desc" ? (a, b) => desc(a, b, orderBy) : (a, b) => desc(b, a, orderBy);
}

const _transformObject = <T, U extends keyof T, V>(obj: T, map: { [K in U]: (t: T) => V }): { [K in U]: V } =>
  mapValues(map, (v) => v(obj));

/**
 * Returns a value for the `pageSizeOptions` prop of pagination controls; the
 * list of various page sizes that we support. Any sizes from the standard list
 * that are larger than the total number of results are discarded. If the total
 * length is less than 100 (the largest standard page size) or the `all`
 * override is set, then a further option of all the available rows is also
 * included.
 */
export const paginationOptions = (resultsLength: number): Array<number | { value: number; label: string }> => [
  ...[5, 10, 25, 100].filter((p) => p < resultsLength),
  ...(resultsLength <= 100 ? [{ value: resultsLength, label: `${resultsLength} (All)` }] : []),
];

export const DataGridColumn = {
  /**
   * Define a new column where the cell's value is simply the property of the
   * object `Row` with key `Field`.
   */
  newColumnWithFieldName<Field extends string, Row extends object & { [K in Field]: unknown }>(
    // Also acts as the unique identifier for the column
    field: Field,
    rest: Omit<GridColDef<Row>, "field">,
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
  newColumnWithValueMapper<Field extends string, Row extends object & { [K in Field]: unknown }>(
    // The name of field to be transformed
    field: Field,
    // Function that does the transformation
    mapFunction: (value: Row[Field]) => string,
    rest: Omit<GridColDef<Row>, "field" | "valueGetter">,
  ): GridColDef<Row> {
    return {
      field,
      valueGetter: (valueBefore: Row[Field]) => mapFunction(valueBefore),
      ...rest,
    };
  },

  /**
   * Define a new column where the cell's value is the output of the
   * `valueGetter` function. If you want to use this field in a table that
   * supports export to CSV then `Value` must be a string.
   */
  newColumnWithValueGetter<Field extends string, Row extends object, Value>(
    // Unique identifier for the column
    field: Field,
    // Function for getting a cell's value from a Row
    valueGetter: (row: Row) => Value,
    rest: Omit<GridColDef<Row>, "field" | "valueGetter">,
  ): GridColDef<Row> {
    return {
      field,
      valueGetter: (_ignore: unknown, row: Row) => valueGetter(row),
      ...rest,
    };
  },
};
