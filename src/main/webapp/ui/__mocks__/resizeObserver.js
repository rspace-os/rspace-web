//@flow
/* eslint-env jest */

/*
 * jest-dom does not define ResizeObserver so we have to define a mock that can
 * be used in tests that depend on components that make use of the browser
 * API.
 */
Object .defineProperty(window, "ResizeObserver", {
  writeable: false,
  value: jest.fn<[void], { ... }>().mockImplementation(() => ({
    observe: () => {},
    disconnect: () => {},
  })),
});
