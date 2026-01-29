
/*
 * jest-dom does not define revokeObjectURL so we have to define a mock that can be
 * used in tests on it
 */
import { it } from "vitest";
Object.defineProperty(window.URL, "revokeObjectURL", {
  writable: true,
  value: () => {},
});


