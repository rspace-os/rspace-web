//@flow strict

/*
 * importMeta wraps `import.meta` which is global value only available within
 * ES modules, and thus isn't available within the jest runtime. This code
 * mocks this function in every test to ensure that any third-party code that
 * relies on it gets a mocked value instead of failing.
 */
jest.mock("../../src/util/importMeta", () => ({
  importMeta: () => ({
    url: "http://example.com",
  }),
}));
