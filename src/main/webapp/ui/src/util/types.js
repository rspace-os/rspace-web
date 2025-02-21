//@flow strict

import { parseString } from "./parsers";
import Result from "./result";

/*
 * Some general purpose type definitions.
 */

/*
 * For adding optional type definitions to invocations of `useState`
 * Usage:
 *  const [count, setCount]: UseState<number> = useState(0);
 */
export type UseStateSetter<T> = (((T) => T) | T) => void;
export type UseState<T> = [T, UseStateSetter<T>];

/*
 * Geometry
 */
export interface Point {
  x: number;
  y: number;
}

/*
 * For sorting data
 */
export type Order = "asc" | "desc";

export function parseOrder(str: string): Result<Order> {
  return Result.first(
    (parseString("asc", str): Result<Order>),
    (parseString("desc", str): Result<Order>)
  );
}

/*
 * The return type of Promise.allSettled
 */
export type AllSettled<A> = Array<
  | {|
      +status: "fulfilled",
      +value: A,
    |}
  | {|
      +status: "rejected",
      +reason: Error,
    |}
>;

/*
 * For managing the state of a two panel layout that on mobile requires the
 * user to toggle between the two panels.
 */
export type Panel = "left" | "right";

/*
 * This is a string in the format as described by ISO 8601.
 */
export type IsoTimestamp = string;

/*
 * This is a string in the format of a URL.
 */
export type URL = string;
