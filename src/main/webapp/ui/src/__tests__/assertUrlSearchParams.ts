/* eslint-env jest */

/*
 * These are some custom assertions for working with instances of
 * URLSearchParams.
 *
 * To use, simply import this script into the test script. Flow supression will
 * be required at each call site as Flow doesn't have a nice way of telling it
 * that `expect` from jest has been extended.
 */

declare global {
  namespace jest {
    interface Matchers<R> {
      urlSearchParamContaining(expected: { [key: string]: string }): R;
      urlSearchParamHasKey(expectedKey: string): R;
    }
    interface Expect {
      urlSearchParamContaining(expected: { [key: string]: string }): any;
      urlSearchParamHasKey(expectedKey: string): any;
    }
  }
}

expect.extend({
  /**
   * Custom assert function for checking that a URLSearchParams object has keys
   * and values resembling that of a passed object.
   *
   * @example
   *  const querySpy = jest
   *    .spyOn(ApiServiceBase.prototype, "query")
   *    .mockImplementation(() =>
   *      Promise.resolve({ data: { containers: [] } })
   *    );
   *  expect(querySpy).toHaveBeenCalledWith(
   *    "containers",
   *    expect.urlSearchParamContaining({ pageNumber: "1" }),
   *  );
   */
  urlSearchParamContaining(
    actual: URLSearchParams,
    expected: { [key: string]: string }
  ) {
    for (const [k, v] of Object.entries(expected)) {
      if (!actual.has(k)) return { pass: false, message: () => "" };
      if (actual.get(k) !== v) return { pass: false, message: () => "" };
    }
    return {
      pass: true,
      message: () => "",
    };
  },

  /**
   * Custom assert function for checking that a URLSearchParams object has a
   * particular key.
   *
   * @example
   *  const querySpy = jest
   *    .spyOn(ApiServiceBase.prototype, "query")
   *    .mockImplementation(() =>
   *      Promise.resolve({ data: { containers: [] } })
   *    );
   *  expect(querySpy).toHaveBeenCalledWith(
   *    "containers",
   *    expect.urlSearchParamHasKey("pageNumber")
   *  );
   */
  urlSearchParamHasKey(actual: URLSearchParams, expectedKey: string) {
    if (actual.has(expectedKey)) return { pass: true, message: () => "" };
    return { pass: false, message: () => "" };
  },
});

export {};
