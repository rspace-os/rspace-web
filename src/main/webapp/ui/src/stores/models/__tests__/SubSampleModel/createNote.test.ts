import {
  describe,
  expect,
  it,
  vi,
} from "vitest";
import { makeMockSubSample } from "./mocking";
import ApiService from "../../../../common/InvApiService";

vi.mock("../../../use-stores", () => () => {});
vi.mock("../../../stores/RootStore", () => ({
  default: () => ({
  peopleStore: {
    currentUser: {
      firstName: "Joe",
      lastName: "Bloggs",
      id: 1,
    },
  },
  uiStore: {
    addAlert: () => {},
    setPageNavigationConfirmation: () => {},
    setDirty: () => {},
    unsetDirty: () => {},
  },
  unitStore: {
    getUnit: () => ({ label: "ml" }),
  },
})
}));
vi.mock("../../../../common/InvApiService", () => ({
  default: {
  post: vi.fn(() => ({ data: { notes: [] } })),

  }}));

describe("action: createNote", () => {
  /*
   * When previewing a subsample, the user can create a note that is
   * immeditaely saved.
   */
  describe("When called in preview state, createNote should", () => {
    it("make a POST call.", () => {
      const subSample = makeMockSubSample();
      subSample.editing = false;
      void subSample.createNote({ content: "A new note" });
      const postSpy = vi.spyOn(ApiService, "post");
      expect(postSpy).toHaveBeenCalledWith("subSamples/1/notes", {
        content: "A new note",
      });
    });
  });

  /*
   * When editing a subsample, the user can create a note, but it is only saved
   * when the whole form is.
   */
  describe("When called in edit state, createNote should", () => {
    it("append to notes observable.", () => {
      const subSample = makeMockSubSample();
      subSample.editing = true;
      (subSample as any).lastEditInput = new Date();
      const setAttributesDirtySpy = vi.spyOn(subSample, "setAttributesDirty");
      void subSample.createNote({ content: "A new note" });
      expect(setAttributesDirtySpy).toHaveBeenCalledWith({
        notes: expect.arrayContaining([
          expect.objectContaining({ content: "A new note" }),
        ]),
      });
    });
  });
});


