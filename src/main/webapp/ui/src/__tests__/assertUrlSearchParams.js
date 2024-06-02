//@flow
/* eslint-env jest */

/*
 * These are some custom assertions for working with instances of
 * URLSearchParams.
 *
 * To use, simply import this script into the test script. Flow supression will
 * be required at each call site as Flow doesn't have a nice way of telling it
 * that `expect` from jest has been extended.
 */

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
   *    // $FlowExpectedError[prop-missing]
   *    expect.urlSearchParamContaining({ pageNumber: "1" }),
   *  );
   */
  urlSearchParamContaining(actual: URLSearchParams, expected: {[string]: string}) {
    for(let [k,v] of Object.entries(expected)) {
      if(!actual.has(k)) return { pass: false, message: () => "" };
      if(actual.get(k) !== v) return { pass: false, message: () => "" };
    }
    return {
      pass: true,
      message: ""
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
   *    // $FlowExpectedError[prop-missing]
   *    expect.urlSearchParamHasKey("pageNumber")
   *  );
   */
  urlSearchParamHasKey(actual: URLSearchParams, expectedKey: string) {
    if(actual.has(expectedKey)) return { pass: true, message: "" };
    return { pass: false, message: "" };
  }
});

