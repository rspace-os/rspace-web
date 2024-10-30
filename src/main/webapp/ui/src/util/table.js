//@flow

import { mapObject, match } from "./Util";
import * as ArrayUtils from "./ArrayUtils";
import { type Axis } from "../stores/definitions/Container";
import { type Order } from "./types";
import { type Column } from "@mui/x-data-grid";
import { type Node } from "react";

function desc<T: string, U>(a: {[T]: U}, b: {[T]: U}, orderBy: T): -1 | 0 | 1 {
  // $FlowExpectedError[invalid-compare]
  if (b[orderBy] < a[orderBy]) {
    return -1;
  }
  // $FlowExpectedError[invalid-compare]
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
function stableSort<T>(
  array: $ReadOnlyArray<T>,
  cmp: (T, T) => -1 | 0 | 1
): Array<T> {
  const stabilizedThis = array.map((el, index) => [el, index]);
  stabilizedThis.sort((a, b) => {
    const order = cmp(a[0], b[0]);
    if (order !== 0) return order;
    return a[1] - b[1];
  });
  return stabilizedThis.map((el) => el[0]);
}

function getSorting<T: string>(
  order: Order,
  orderBy: T
): ({ [T]: any }, { [T]: any }) => -1 | 0 | 1 {
  return order === "desc"
    ? (a, b) => desc(a, b, orderBy)
    : (a, b) => -desc(a, b, orderBy);
}

const transformObject = <T, U, V>(obj: T, map: { [U]: (T) => V }): { [U]: V } =>
  mapObject((k, v) => v(obj), map);

const getSortingSpecified =
  <T: {}>(
    order: Order,
    orderBy: string,
    map: { [string]: (T) => string }
  ): ((T, T) => -1 | 0 | 1) =>
  (a: T, b: T) =>
    // $FlowExpectedError[incompatible-return] {-1,0,1} * {-1,1} is indeed {-1,0,1}
    desc(transformObject(a, map), transformObject(b, map), orderBy) *
    (order === "desc" ? 1 : -1);

/**
 * Returns a value for the `pageSizeOptions` prop of pagination controls; the
 * list of various page sizes that we support. Any sizes from the standard list
 * that are larger than the total number of results are discarded. If the total
 * length is less than 100 (the largest standard page size) or the `all`
 * override is set, then a further option of all the available rows is also
 * included.
 */
const paginationOptions = (
  resultsLength: number
): Array<number | {| value: number, label: string |}> => [
  ...([5, 10, 25, 100].filter((p) => p < resultsLength): Array<number>),
  ...(resultsLength <= 100
    ? [{ value: resultsLength, label: `${resultsLength} (All)` }]
    : []),
];

const arrayToN = (n: number): Array<number> => {
  return Array.from({ length: n }, (x, i) => i + 1);
};

export const encodeA1Z26 = (num: number): string =>
  String.fromCharCode(64 + num);

const layoutToLabels = (
  layout: Axis,
  n: number
): Array<{ value: number, label: string | number }> =>
  ArrayUtils.zipWith(
    arrayToN(n),
    match<Axis, any>([
      [(l) => l === "N123", arrayToN(n)],
      [(l) => l === "N321", arrayToN(n).reverse()],
      [(l) => l === "ABC", arrayToN(n).map(encodeA1Z26)],
      [(l) => l === "CBA", arrayToN(n).reverse().map(encodeA1Z26)],
    ])(layout),
    (value: number, label: string | number) => ({ value, label })
  );

type ColumnProps<Row: { ... }, Value> = {|
  headerName: string,
  renderCell?: (params: {
    row: Row,
    value: Value,
    tabIndex: number,
    ...
  }) => Node,
  valueFormatter?: (Value) => Node,
  hideable?: boolean,
  width?: number,
  flex?: number,
  disableColumnMenu?: boolean,
  sortable?: boolean,
  headerClassName?: string,
  disableExport?: boolean,
  display?: "text" | "flex",
  resizable?: boolean,
|};

const DataGridColumn = {
  /**
   * Define a new column where the cell's value is simply the property of the
   * object `Row` with key `Field`.
   */
  newColumnWithFieldName<Row: { ... }, Field: string>(
    // Also acts as the unique identifier for the column
    field: Field,
    // $FlowExpectedError[invalid-computed-prop]
    rest: ColumnProps<Row, Row[Field]>
  ): Column<Row> {
    return {
      field,
      ...rest,
    };
  },
  /**
   * Define a new column where the cell's value is the output of the
   * `valueGetter` function.
   */
  newColumnWithValueGetter<Row: { ... }, Field: string>(
    // Unique identifier for the column
    field: Field,
    // $FlowExpectedError[invalid-computed-prop]
    valueGetter: (Row[Field], Row) => string,
    // $FlowExpectedError[invalid-computed-prop]
    rest: ColumnProps<Row, Row[Field]>
  ): Column<Row> {
    return {
      field,
      valueGetter,
      ...rest,
    };
  },
};

export {
  desc,
  stableSort,
  getSorting,
  paginationOptions,
  getSortingSpecified,
  layoutToLabels,
  DataGridColumn,
};
