//@flow
/* eslint-env jest */

/*
 * jest-dom does not define matchMedia so we have to define a mock that can be
 * used in tests that depend on components that make use of the browser API.
 * This code is taken from the Jest docs:
 * https://jestjs.io/docs/manual-mocks#mocking-methods-which-are-not-implemented-in-jsdom
 */
Object.defineProperty(window, "matchMedia", {
  writable: true,
  value: jest.fn<[string], { ... }>().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: jest.fn(), // deprecated
    removeListener: jest.fn(), // deprecated
    addEventListener: jest.fn(),
    removeEventListener: jest.fn(),
    dispatchEvent: jest.fn(),
  })),
});
