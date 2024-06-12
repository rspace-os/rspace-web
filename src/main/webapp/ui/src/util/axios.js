//@flow strict

import Result from "./result";

/*
 * These are just some general utility functions for working with the axios
 * library.
 */

/*
 * This function gets a particular header from the response object of a call to
 * axios. If the header is available then it is returned wrapped in an
 * Result.Ok, otherwise Result.Error is returned. There are a few
 * reasons for why the header may not be available:
 *  - It is simply not in the response object
 *  - It is in the response object but the browser denies the JS runtime from
 *    accessing it because the network call was a cross-origin one and the
 *    Access-Control-Exposes-Headers header has not allowed access. For more
 *    information on this CORS header, see the MDN docs:
 *    https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Access-Control-Expose-Headers
 */
export function getHeader(
  response: { headers: { ... }, ... },
  headerName: string
): Result<string> {
  if (typeof response.headers[headerName] === "undefined")
    return Result.Error([new Error(`Header "${headerName}" is missing`)]);
  return Result.Ok(response.headers[headerName]);
}
