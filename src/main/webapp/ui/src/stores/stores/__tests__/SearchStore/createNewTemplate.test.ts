import { describe, expect, test, vi } from 'vitest';
import getRootStore from "../../RootStore";
import TemplateModel from "../../../models/TemplateModel";
describe("method: createNewTemplate", () => {
  test("Should return a new template model", async () => {
    const { searchStore, peopleStore } = getRootStore();
    vi
      .spyOn(peopleStore, "fetchCurrentUsersGroups")
      .mockImplementation(() => Promise.resolve([]));
    const template = await searchStore.createNewTemplate();
    expect(template.id).toBe(null);
  });
  test("Should not call fetchAdditionalInfo on the new template", async () => {
    const { searchStore } = getRootStore();
    const spy = vi.spyOn(TemplateModel.prototype, "fetchAdditionalInfo");
    await searchStore.createNewTemplate();
    expect(spy).not.toHaveBeenCalled();
  });
});

