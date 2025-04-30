/* eslint-env jest */

/*
 * jest-dom does not define ResizeObserver so we have to define a mock that can
 * be used in tests that depend on components that make use of the browser
 * API.
 */
class ResizeObserverMock {
  observe = jest.fn();
  unobserve = jest.fn();
  disconnect = jest.fn();
}

Object.defineProperty(window, "ResizeObserver", {
  writable: true,
  configurable: true,
  value: jest.fn().mockImplementation(() => new ResizeObserverMock()),
});
