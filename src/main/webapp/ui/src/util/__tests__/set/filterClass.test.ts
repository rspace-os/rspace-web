/*
 */
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import RsSet from "../../set";

describe("filterClass", () => {
  it("Simple example", () => {
    class Foo {}
    class Bar {}
    const set = new RsSet([new Foo(), new Foo(), new Bar()]);

    expect(set.filterClass(Foo).size).toBe(2);
  });
});