
import { describe, expect, test, vi } from 'vitest';
import { containerAttrs } from "../../__tests__/ContainerModel/mocking";
import { sampleAttrs, makeMockSample } from "../../__tests__/SampleModel/mocking";
import { subsampleAttrs } from "../../__tests__/SubSampleModel/mocking";
import { personAttrs } from "../../__tests__/PersonModel/mocking";
import AlwaysNewFactory from "../AlwaysNewFactory";
import { type GlobalId } from "../../../definitions/BaseRecord";

vi.mock("../../../stores/getRootStore", () => ({
  default: () => ({
  peopleStore: {},
  unitStore: { getUnit: () => ({ label: "ml" }) },
})

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
  describe("When called with a versioned Global ID, newRecord should", () => {
    /*
     * Historical versions of records are served with versioned Global IDs
     * (e.g. SS4v1); they must instantiate the same record class as their
     * live counterparts rather than being rejected as unknown.
     */
    test("instantiate a sample from e.g. SA42v2.", () => {
      const factory = new AlwaysNewFactory();
      const sample = factory.newRecord(
        sampleAttrs({ globalId: "SA42v2" }) as Record<string, unknown> & {
          globalId: GlobalId;
        }
      );
      expect(sample.recordType).toBe("sample");
    });

    test("instantiate a subsample from e.g. SS4v1.", () => {
      const factory = new AlwaysNewFactory();
      const subSample = factory.newRecord(
        subsampleAttrs({
          globalId: "SS4v1",
          sample: makeMockSample(),
        }) as Record<string, unknown> & {
          globalId: GlobalId;
        }
      );
      expect(subSample.recordType).toBe("subSample");
    });

    test("instantiate a container from e.g. IC9v3.", () => {
      const factory = new AlwaysNewFactory();
      const container = factory.newRecord(
        containerAttrs({ globalId: "IC9v3" }) as Record<string, unknown> & {
          globalId: GlobalId;
        }
      );
      expect(container.recordType).toBe("container");
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

