
/*
 * jest-dom does not define ResizeObserver so we have to define a mock that can
 * be used in tests that depend on components that make use of the browser
 * API.
 */
import { vi } from "vitest";
class ResizeObserverMock {
  observe = vi.fn();
  unobserve = vi.fn();
  disconnect = vi.fn();
}

function ResizeObserver() {
  return new ResizeObserverMock();
}

Object.defineProperty(window, "ResizeObserver", {
  writable: true,
  configurable: true,
  value: ResizeObserver,
});

