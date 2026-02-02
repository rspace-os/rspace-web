import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import RsSet from "../../set";

describe("subtractWithEq", () => {
  it("Simple example", () => {
    expect(
      new RsSet([{ id: 1 }, { id: 2 }])
        .subtractWithEq(new RsSet([{ id: 2 }]), (a, b) => a.id === b.id)
        .map(({ id }) => id)
        .isSame(new RsSet([1]))
    ).toBe(true);
  });
});