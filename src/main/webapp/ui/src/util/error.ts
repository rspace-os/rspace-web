import Result from "./result";
import * as Parsers from "./parsers";

/**
 * This script contains various common general-purpose error classes and utility
 * functions that can be used throughout the application
 */

/**
 * When the system has entered an invalid state and further execution is
 * impossible.
 */
export class InvalidState extends Error {
  constructor(message: string) {
    super(message);
    this.name = "InvalidState";
  }
}

/**
 * A string could not be parsed into the expected format.
 */
export class UnparsableString extends Error {
  constructor(string: string, message: string) {
    super(`Error when parsing "${string}": ${message}.`);
    this.name = "UnparsableString";
  }
}

/**
 * The user cancelled an operation and we're using exception handling as a way
 * to jump right up the call stack to where the operation started.
 */
export class UserCancelledAction extends Error {
  constructor(message: string) {
    super(message);
    this.name = "UserCancelledAction";
  }
}

/**
 * For when the data in local storage is not in the required format.
 */
export class InvalidLocalStorageState extends Error {
  constructor(message: string) {
    super(message);
    this.name = "InvalidLocalStorageState";
  }
}

/**
 * Get the error message from either an Axios response object, an generic Error,
 * or else the passed fallback.
 *
 * @arg error     Anything, and if it is an Axios response or an Error then the
 *                message is extracted.
 * @arg fallback  The value returned if `error` is neither an Axios response
 *                nor an Error
 * @example
 *   getErrorMessage(new Error("example"), "Unknown reason")
 */
export function getErrorMessage(error: unknown, fallback: string): string {
  return Parsers.objectPath(["response", "data", "message"], error)
    .flatMap(Parsers.isString)
    .orElseTry(() =>
      Parsers.isObject(error).flatMap((e) =>
        e instanceof Error ? Result.Ok(e.message) : Result.Error<string>([])
      )
    )
    .orElse(fallback);
}
