/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";

import { containerAttrs } from "../../__tests__/ContainerModel/mocking";
import { personAttrs } from "../../__tests__/PersonModel/mocking";
import AlwaysNewFactory from "../AlwaysNewFactory";

jest.mock("../../../stores/RootStore", () => () => ({
  peopleStore: {},
})); // break import cycle

describe("AlwaysNewFactory", () => {
  describe("When called with the same Global ID, newRecord should", () => {
    test("return different objects.", () => {
      const factory = new AlwaysNewFactory();
      const container1 = factory.newRecord(containerAttrs());
      const container2 = factory.newRecord(containerAttrs());

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
