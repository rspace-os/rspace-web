/*
 * @vitest-environment jsdom
 */
import { describe, test, expect, vi } from "vitest";
import { type Note } from "../../SubSampleModel";

vi.mock("../../../use-stores", () => () => {});
vi.mock("../../../stores/RootStore", () => ({
  default: () => ({})
}));

describe("type: Note", () => {
  /*
   * Objects of the type Note are passed to API calls when creating notes and
   * as such must be JSON serialisable.
   */
  test("Is JSON serialisable.", () => {
    const aNote: Note = {
      createdBy: {
        firstName: "Joe",
        lastName: "Bloggs",
        id: 4,
      },
      created: new Date().toISOString(),
      content: "foo",
    };

    expect(JSON.stringify(aNote)).toEqual(expect.any(String));
  });
});


