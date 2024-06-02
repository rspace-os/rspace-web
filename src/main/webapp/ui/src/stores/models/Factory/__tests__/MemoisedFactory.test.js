/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";

import { containerAttrs } from "../../__tests__/ContainerModel/mocking";
import { personAttrs } from "../../__tests__/PersonModel/mocking";
import MemoisedFactory from "../MemoisedFactory";
import ContainerModel from "../../ContainerModel";

jest.mock("../../../stores/RootStore", () => () => ({
  peopleStore: {},
})); // break import cycle

describe("MemoisedFactory", () => {
  describe("When called with the same Global ID, newRecord should", () => {
    test("return the same object.", () => {
      const factory = new MemoisedFactory();
      const container1 = factory.newRecord(containerAttrs());
      const container2 = factory.newRecord(containerAttrs());

      expect(container1).toBe(container2);
    });
  });

  describe("When called with the same Person ID, newPerson should", () => {
    test("return the same object.", () => {
      const factory = new MemoisedFactory();
      const person1 = factory.newPerson(personAttrs());
      const person2 = factory.newPerson(personAttrs());

      expect(person1).toBe(person2);
    });
  });

  describe("When called with summary record and then full record, newRecord should", () => {
    test("not update the existing record with the new data.", () => {
      const factory = new MemoisedFactory();

      // $FlowExpectedError[incompatible-type] we know its going to be a container
      const containerWithLastParent: ContainerModel = factory.newRecord(
        containerAttrs({
          lastNonWorkbenchParent: containerAttrs({
            id: 2,
            globalId: "IC2",
            permittedActions: [],
          }),
        })
      );
      const container2: ContainerModel =
        // $FlowExpectedError[incompatible-type] we know its going to be not null
        containerWithLastParent.lastNonWorkbenchParent;
      expect([...container2.permittedActions].length).toBe(0);

      factory.newRecord(containerAttrs({ id: 2, globalId: "IC2" }));
      expect([...container2.permittedActions].length).toBe(0);
    });
  });
});
