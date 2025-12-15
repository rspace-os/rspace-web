/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";

import { containerAttrs } from "../../__tests__/ContainerModel/mocking";
import { personAttrs } from "../../__tests__/PersonModel/mocking";
import MemoisedFactory from "../MemoisedFactory";
import ContainerModel from "../../ContainerModel";
import { GlobalId } from "@/stores/definitions/BaseRecord";

jest.mock("../../../stores/RootStore", () => () => ({
  peopleStore: {},
})); // break import cycle

describe("MemoisedFactory", () => {
  describe("When called with the same Global ID, newRecord should", () => {
    test("return the same object.", () => {
      const factory = new MemoisedFactory();
      const container1 = factory.newRecord(
        containerAttrs() as { globalId: GlobalId }
      );
      const container2 = factory.newRecord(
        containerAttrs() as { globalId: GlobalId }
      );

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

      const containerWithLastParent = factory.newRecord(
        containerAttrs({
          lastNonWorkbenchParent: containerAttrs({
            id: 2,
            globalId: "IC2",
            permittedActions: [],
          }),
        }) as { globalId: GlobalId }
      ) as ContainerModel;
      const container2 =
        // @ts-expect-error Yes we're fiddling with private properties here
        containerWithLastParent.lastNonWorkbenchParent as ContainerModel;
      expect([...container2.permittedActions].length).toBe(0);

      factory.newRecord(
        containerAttrs({ id: 2, globalId: "IC2" }) as { globalId: GlobalId }
      );
      expect([...container2.permittedActions].length).toBe(0);
    });
  });
});
