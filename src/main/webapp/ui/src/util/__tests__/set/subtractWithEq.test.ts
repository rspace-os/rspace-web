import { describe, expect, test } from 'vitest';
import RsSet from "../../set";

describe("subtractWithEq", () => {
  test("Simple example", () => {
    expect(
      new RsSet([{ id: 1 }, { id: 2 }])
        .subtractWithEq(new RsSet([{ id: 2 }]), (a, b) => a.id === b.id)
        .map(({ id }) => id)
        .isSame(new RsSet([1]))
    ).toBe(true);
  });
});