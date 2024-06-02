/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import { makeMockSubSample } from "./mocking";
import ApiService from "../../../../common/InvApiService";

jest.mock("../../../use-stores", () => () => {});
jest.mock("../../../stores/RootStore", () => () => ({
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
}));
jest.mock("../../../../common/InvApiService", () => ({
  post: jest.fn(() => ({ data: { notes: [] } })),
}));

describe("action: createNote", () => {
  /*
   * When previewing a subsample, the user can create a note that is
   * immeditaely saved.
   */
  describe("When called in preview state, createNote should", () => {
    test("make a POST call.", () => {
      const subSample = makeMockSubSample();
      subSample.editing = false;
      void subSample.createNote({ content: "A new note" });
      const postSpy = jest.spyOn(ApiService, "post");
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
    test("append to notes observable.", () => {
      const subSample = makeMockSubSample();
      subSample.editing = true;
      subSample.lastEditInput = new Date();
      const setAttributesDirtySpy = jest.spyOn(subSample, "setAttributesDirty");
      void subSample.createNote({ content: "A new note" });
      expect(setAttributesDirtySpy).toHaveBeenCalledWith({
        notes: expect.arrayContaining([
          expect.objectContaining({ content: "A new note" }),
        ]),
      });
    });
  });
});
