/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import getRootStore from "../../RootStore";
import TemplateModel from "../../../models/TemplateModel";

describe("method: createNewTemplate", () => {
  test("Should return a new template model", async () => {
    const { searchStore, peopleStore } = getRootStore();
    jest
      .spyOn(peopleStore, "fetchCurrentUsersGroups")
      .mockImplementation(() => Promise.resolve([]));
    const template = await searchStore.createNewTemplate();
    expect(template.id).toBe(null);
  });

  test("Should not call fetchAdditionalInfo on the new template", async () => {
    const { searchStore } = getRootStore();
    const spy = jest.spyOn(TemplateModel.prototype, "fetchAdditionalInfo");
    await searchStore.createNewTemplate();
    expect(spy).not.toHaveBeenCalled();
  });
});
