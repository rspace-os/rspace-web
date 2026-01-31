/// <reference types="vitest/globals" />
/// <reference types="@testing-library/jest-dom/vitest" />

declare module "vitest" {
  interface Assertion<T = any> {
    toBeAccessible(): Promise<void>;
  }
}

