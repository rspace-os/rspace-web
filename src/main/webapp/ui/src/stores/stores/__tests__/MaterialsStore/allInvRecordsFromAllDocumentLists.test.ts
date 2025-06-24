/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import { cleanup } from "@testing-library/react";
import "@testing-library/jest-dom";
import getRootStore from "../../RootStore";
import { containerAttrs } from "../../../models/__tests__/ContainerModel/mocking";
import { subsampleAttrs } from "../../../models/__tests__/SubSampleModel/mocking";
import { sampleAttrs } from "../../../models/__tests__/SampleModel/mocking";
import { ListOfMaterials } from "../../../models/MaterialsModel";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("allInvRecordsFromAllDocumentLists", () => {
  test("The same record across multiple lists of materials of one field should list the record once.", () => {
    const { materialsStore } = getRootStore();
    const container = containerAttrs();
    materialsStore.documentLists.set(1, [
      new ListOfMaterials({
        id: 1,
        name: "foo",
        description: "",
        elnFieldId: 1,
        materials: [{ invRec: container, usedQuantity: null }],
      }),
      new ListOfMaterials({
        id: 2,
        name: "bar",
        description: "",
        elnFieldId: 1,
        materials: [{ invRec: container, usedQuantity: null }],
      }),
    ]);

    expect(materialsStore.allInvRecordsFromAllDocumentLists.size).toBe(1);
  });
  test("The same record across multiple lists of materials of multiple fields should list the record once.", () => {
    const { materialsStore } = getRootStore();
    const container = containerAttrs();
    materialsStore.documentLists.set(1, [
      new ListOfMaterials({
        id: 1,
        name: "foo",
        description: "",
        elnFieldId: 1,
        materials: [{ invRec: container, usedQuantity: null }],
      }),
    ]);
    materialsStore.documentLists.set(2, [
      new ListOfMaterials({
        id: 2,
        name: "bar",
        description: "",
        elnFieldId: 1,
        materials: [{ invRec: container, usedQuantity: null }],
      }),
    ]);

    expect(materialsStore.allInvRecordsFromAllDocumentLists.size).toBe(1);
  });
  test("Different records with the same id in a single list of materials of a single field should list both records.", () => {
    const { materialsStore } = getRootStore();

    // having to set this back to empty set because the state of the tests are bleeding into eachother
    materialsStore.documentLists = new Map();

    const sample = sampleAttrs({
      id: 1,
      globalId: "SA1",
    });
    const subsample = subsampleAttrs({
      id: 1,
      globalId: "SS1",
      sample,
    });
    materialsStore.documentLists.set(1, [
      new ListOfMaterials({
        id: 1,
        name: "foo",
        description: "",
        elnFieldId: 1,
        materials: [
          { invRec: subsample, usedQuantity: null },
          { invRec: sample, usedQuantity: null },
        ],
      }),
    ]);

    expect(materialsStore.allInvRecordsFromAllDocumentLists.size).toBe(2);
  });
});
