import { expect } from "vitest";

export const expectAccessible = (element: HTMLElement): Promise<void> => {
  // Vitest cannot see the matcher registered at runtime.
  // eslint-disable-next-line vitest/valid-expect
  const assertion = expect(element) as unknown as {
    toBeAccessible: () => Promise<void>;
  };
  return assertion.toBeAccessible();
};
