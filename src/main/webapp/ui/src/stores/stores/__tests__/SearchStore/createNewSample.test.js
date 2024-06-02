/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import getRootStore from "../../RootStore";
import SampleModel from "../../../models/SampleModel";

describe("method: createNewSample", () => {
  test("Should return a new sample model", async () => {
    const { searchStore, peopleStore } = getRootStore();
    jest
      .spyOn(peopleStore, "fetchCurrentUsersGroups")
      .mockImplementation(() => Promise.resolve([]));
    const sample = await searchStore.createNewSample();
    expect(sample.id).toBe(null);
  });

  test("Should not call fetchAdditionalInfo on the new sample.", async () => {
    const { searchStore } = getRootStore();
    const spy = jest.spyOn(SampleModel.prototype, "fetchAdditionalInfo");
    await searchStore.createNewSample();
    expect(spy).not.toHaveBeenCalled();
  });
});
