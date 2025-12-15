/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import fc from "fast-check";
import { makeMockSample } from "../SampleModel/mocking";
import { makeMockBench, makeMockContainer } from "../ContainerModel/mocking";
import {
  makeMockSubSample,
  subSampleAttrsArbitrary,
} from "../SubSampleModel/mocking";
import getRootStore from "../../../stores/RootStore";
import { personAttrs } from "../PersonModel/mocking";
import PersonModel from "../../PersonModel";

const mockRootStore = {
  peopleStore: {
    currentUser: null,
  },
  unitStore: {
    getUnit: () => ({ label: "ml" }),
  },
};

jest.mock("../../../../common/InvApiService", () => {});
jest.mock("../../../../stores/stores/RootStore", () => () => mockRootStore);

describe("computed: recordLinkLabel", () => {
  test("Samples should always return their name.", () => {
    const sample = makeMockSample();
    expect(sample.recordLinkLabel).toBe(sample.name);
  });
  test("Subsamples should always return their name.", () => {
    fc.assert(
      fc.property(subSampleAttrsArbitrary, (subSampleAttrs) => {
        const subsample = makeMockSubSample(subSampleAttrs);
        expect(subsample.recordLinkLabel).toBe(subsample.name);
      })
    );
  });
  test("Regular containers should always return their name.", () => {
    const container = makeMockContainer();
    expect(container.recordLinkLabel).toBe(container.name);
  });

  test("A bench, where the current user is unknown, should show the owner's name.", () => {
    const bench = makeMockBench({
      owner: {
        ...personAttrs(),
        username: "foo",
        firstName: "Joe",
        lastName: "Bloggs",
      },
    });
    expect(bench.recordLinkLabel).toBe("Joe Bloggs's Bench");
  });
  test("A bench, where the current user is the owner, should show 'My Bench'.", () => {
    const owner = personAttrs();
    const bench = makeMockBench({ owner });
    getRootStore().peopleStore.currentUser = new PersonModel(owner);
    expect(bench.recordLinkLabel).toBe("My Bench");
  });
  test("A bench, where the current user is not the owner, should show the owner's name.", () => {
    const bench = makeMockBench({
      owner: {
        ...personAttrs(),
        username: "foo",
        firstName: "Joe",
        lastName: "Bloggs",
      },
    });
    getRootStore().peopleStore.currentUser = new PersonModel(
      personAttrs({
        ...personAttrs(),
        username: "bar",
      })
    );
    expect(bench.recordLinkLabel).toBe("Joe Bloggs's Bench");
  });
});
