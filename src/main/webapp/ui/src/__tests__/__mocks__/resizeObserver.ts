/*
 * jsdom does not define ResizeObserver, so setup.ts installs this mock for
 * every jsdom test.
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
