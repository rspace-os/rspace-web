/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import fc from "fast-check";
import { arbRsSet } from "../../../../util/__tests__/set/helpers";
import { unionWith } from "../../../../util/set";
import { makeMockContainer, containerAttrs } from "./mocking";
import {
  makeMockSubSample,
  subSampleAttrsArbitrary,
} from "../SubSampleModel/mocking";
import { makeMockSample } from "../SampleModel/mocking";
import LocationModel from "../../LocationModel";
import { type SubSampleAttrs } from "../../SubSampleModel";

describe("computed: siblingGroups", () => {
  test("Empty container should have zero siblingGroups.", () => {
    const container = makeMockContainer({
      locations: [],
    });
    expect(container.siblingGroups.size).toBe(0);
  });

  test("Container with only containers should have zero siblingGroups.", () => {
    const container = makeMockContainer({
      id: 1,
      globalId: "IC1",
      locations: [],
    });
    const inner = makeMockContainer({
      globalId: "IC2",
      id: 2,
      parentContainers: [containerAttrs({ globalId: "IC1" })],
    });
    container.locations = [
      new LocationModel({
        id: null,
        coordX: 1,
        coordY: 1,
        content: inner,
        parentContainer: container,
      }),
    ];
    expect(container.siblingGroups.size).toBe(0);
  });

  test("Container with one subsample should have one siblingGroup.", () => {
    const container = makeMockContainer({
      id: 1,
      globalId: "IC1",
      locations: [],
    });
    const subsample = makeMockSubSample();
    container.locations = [
      new LocationModel({
        id: null,
        coordX: 1,
        coordY: 1,
        content: subsample,
        parentContainer: container,
      }),
    ];
    expect(container.siblingGroups.size).toBe(1);
  });

  test("Container with `n` subsamples, each from the same sample, should have one siblingGroup.", () => {
    fc.assert(
      fc.property(
        fc
          .array(
            arbRsSet(subSampleAttrsArbitrary, { maxSize: 1, minSize: 1 }),
            { minLength: 1, maxLength: 100 }
          )
          .map<Array<SubSampleAttrs>>((subsamples) =>
            unionWith((ss) => ss.id, subsamples).toArray()
          ), // ensures all ids are unique
        (subsampleAttrs) => {
          const sample = makeMockSample({
            id: 1,
            globalId: "SA1",
          });
          const subsamples = subsampleAttrs.map((ss) => {
            const subsample = makeMockSubSample(ss);
            subsample.sample = sample;
            return subsample;
          });
          const container = makeMockContainer({
            id: 1,
            globalId: "IC1",
            locations: [],
          });
          container.locations = subsamples.map((ss, i) => {
            return new LocationModel({
              id: i,
              coordX: 1,
              coordY: i,
              content: ss,
              parentContainer: container,
            });
          });
          expect(container.siblingGroups.size).toBe(1);
        }
      )
    );
  });

  test("Container with `n` subsamples, each from a different sample, should have `n` siblingGroups.", () => {
    fc.assert(
      fc.property(
        fc
          .array(arbRsSet(subSampleAttrsArbitrary, { maxSize: 1, minSize: 1 }))
          .map<Array<SubSampleAttrs>>((subsamples) =>
            unionWith((ss) => ss.id, subsamples).toArray()
          ), // ensures all ids are unique
        (subsampleAttrs) => {
          const subsamples = subsampleAttrs.map((ss, i: number) => {
            const subsample = makeMockSubSample(ss);
            const sample = makeMockSample({
              id: i,
              globalId: `SA${i}`,
            });
            subsample.sample = sample;
            return subsample;
          });
          const container = makeMockContainer({
            id: 1,
            globalId: "IC1",
            locations: [],
          });
          container.locations = subsamples.map((ss, i) => {
            return new LocationModel({
              id: i,
              coordX: 1,
              coordY: i,
              content: ss,
              parentContainer: container,
            });
          });
          expect(container.siblingGroups.size).toBe(subsamples.length);
        }
      )
    );
  });
});
