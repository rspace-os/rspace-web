/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";

import { containerAttrs } from "../../__tests__/ContainerModel/mocking";
import { personAttrs } from "../../__tests__/PersonModel/mocking";
import AlwaysNewFactory from "../AlwaysNewFactory";
import { type GlobalId } from "../../../definitions/BaseRecord";

jest.mock("../../../stores/RootStore", () => () => ({
  peopleStore: {},
})); // break import cycle

describe("AlwaysNewFactory", () => {
  describe("When called with the same Global ID, newRecord should", () => {
    test("return different objects.", () => {
      const factory = new AlwaysNewFactory();
      const attrs1 = containerAttrs({ globalId: "IC1" });
      const attrs2 = containerAttrs({ globalId: "IC1" });
      // Type assertion to satisfy the strict globalId requirement
      const container1 = factory.newRecord(
        attrs1 as Record<string, unknown> & { globalId: GlobalId }
      );
      const container2 = factory.newRecord(
        attrs2 as Record<string, unknown> & { globalId: GlobalId }
      );

      expect(container1).not.toBe(container2);
    });
  });

  describe("When called with the same Person ID, newPerson should", () => {
    test("return different objects.", () => {
      const factory = new AlwaysNewFactory();
      const person1 = factory.newPerson(personAttrs());
      const person2 = factory.newPerson(personAttrs());

      expect(person1).not.toBe(person2);
    });
  });
});
