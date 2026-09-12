import * as Parsers from "./parsers";
import Result from "./result";

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
    .orElseTry(() => Parsers.objectPath(["response", "data", "exceptionMessage"], error))
    .flatMap(Parsers.isString)
    .orElseTry(() =>
      Parsers.isObject(error).flatMap((e) => (e instanceof Error ? Result.Ok(e.message) : Result.Error<string>([]))),
    )
    .orElse(fallback);
}

/**
 * A leading request path, as the Inventory API prefixes each of its field-scoped errors with:
 * "origins[0].amountTaken: ". Deliberately narrow - it requires a dotted or indexed path, so a
 * message that merely opens with a word and a colon ("Warning: stock is low") keeps its lead-in.
 */
const FIELD_PATH_PREFIX = /^[A-Za-z_]\w*(?:\[\d+\]|\.\w+)+:\s*/;

/**
 * The reason an API call was rejected, for showing to the user.
 *
 * A field-scoped 400 from the Inventory API carries "Errors detected: N" in `message` and the
 * actual reasons in `errors`, each prefixed by the request path it applies to
 * ("origins[0].amountTaken: Cannot take more..."). `getErrorMessage` shows only `message`, which
 * tells the user nothing they can act on. Only the first error is returned: the user fixes one
 * thing at a time, and the API already orders them by request index.
 *
 * Every other API response also carries an `errors` array, holding a single empty string, because
 * `ApiError` wraps its fourth constructor argument in a singleton list. A blank entry (or one that
 * is nothing but a path) therefore has to fall through to `message`, or a 409, 404 or 403 would
 * show a titled alert with no message in it.
 *
 * @arg error     Anything; the detail is extracted if it is an Axios error carrying one.
 * @arg fallback  Passed through to {@link getErrorMessage} when there is no detail.
 * @arg formatOriginIndex  Renders "which origin", given the reason and a 1-based index. Omit it and
 *   the index is dropped; see {@link ORIGIN_INDEX}.
 */
export function getApiErrorDetail(
  error: unknown,
  fallback: string,
  formatOriginIndex?: (reason: string, index: number) => string,
): string {
  return (
    Parsers.objectPath(["response", "data", "errors"], error)
      .flatMap(Parsers.isArray)
      .flatMap(([first]) => (typeof first === "undefined" ? Result.Error<unknown>([]) : Result.Ok(first)))
      .flatMap(Parsers.isString)
      .map((detail) => withOriginIndex(detail, formatOriginIndex))
      .flatMap((detail) => (detail.length > 0 ? Result.Ok(detail) : Result.Error<string>([])))
      // Always Ok, so this is the last step: there is no trailing .orElse, which was unreachable and
      // read as a live fallback (parallel review, FE11).
      .orElseTry(() => Result.Ok(getErrorMessage(error, fallback)))
      .orElse(fallback)
  );
}

/** An `origins[N]` path, whose index is the one part of the path the user needs. */
const ORIGIN_INDEX = /^origins\[(\d+)\]/;

/**
 * The reason, with the request path stripped - except that an origin's index is kept.
 *
 * Stripping the whole path turned a Pool rejection into "Cannot take more than the subsample holds"
 * with no indication which of five origins was at fault, leaving the user to guess (parallel
 * review, FE11). The index is 1-based here because it is read by a person, not sent back.
 *
 * The WORDING is the caller's, because this module has no `t` and the reason it is being appended to
 * was already localized by the server: concatenating " (origin 3)" here shipped half an English
 * sentence to every non-English user, and not every locale writes an aside in trailing parentheses
 * (parallel review, A4). A caller that passes no formatter is saying it has no way to word one, so
 * the index is dropped rather than guessed at in English.
 */
function withOriginIndex(detail: string, format?: (reason: string, index: number) => string): string {
  const origin = ORIGIN_INDEX.exec(detail);
  const reason = detail.replace(FIELD_PATH_PREFIX, "").trim();
  if (!origin || !format || reason.length === 0) return reason;
  return format(reason, Number(origin[1]) + 1);
}
