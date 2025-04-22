/* eslint-env jest */

/*
 * jest-dom does not define revokeObjectURL so we have to define a mock that can be
 * used in tests on it
 */
Object.defineProperty(window.URL, "revokeObjectURL", {
  writable: true,
  value: () => {},
});

