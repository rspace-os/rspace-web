/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import getRootStore from "../../RootStore";
import PersonModel from "../../../models/PersonModel";
import { makeMockContainer } from "../../../models/__tests__/ContainerModel/mocking";
import fc from "fast-check";
import { arbitraryGroup } from "../../../definitions/__tests__/Group/helper";
import ContainerModel from "../../../models/ContainerModel";

describe("method: createNewContainer", () => {
  test("Should return a new container model", async () => {
    const { searchStore, peopleStore } = getRootStore();
    jest
      .spyOn(peopleStore, "fetchCurrentUsersGroups")
      .mockImplementation(() => Promise.resolve([]));
    const container = await searchStore.createNewContainer();
    expect(container.id).toBe(null);
  });

  test("Should return an object with a parentContainer of the current user's bench", async () => {
    const bench = makeMockContainer({
      id: 9,
      globalId: "BE1",
    });
    jest
      .spyOn(PersonModel.prototype, "getBench")
      .mockImplementation(() => Promise.resolve(bench));

    const { searchStore, peopleStore } = getRootStore();
    peopleStore.currentUser = new PersonModel({
      id: 1,
      username: "foo",
      firstName: "foo",
      lastName: "bar",
      hasPiRole: false,
      hasSysAdminRole: false,
      email: null,
      workbenchId: 1,
      _links: [],
    });
    jest
      .spyOn(peopleStore, "fetchCurrentUsersGroups")
      .mockImplementation(() => Promise.resolve([]));

    const container = await searchStore.createNewContainer();
    expect(container.parentContainers).toEqual([bench]);
  });

  test("Should return an object with sharedWith set to current groups", async () => {
    await fc.assert(
      fc.asyncProperty(fc.array(arbitraryGroup), async (groups) => {
        const bench = makeMockContainer({ id: 9, globalId: "BE1" });
        jest
          .spyOn(PersonModel.prototype, "getBench")
          .mockImplementation(() => Promise.resolve(bench));

        const { searchStore, peopleStore } = getRootStore();
        peopleStore.currentUser = new PersonModel({
          id: 1,
          username: "foo",
          firstName: "foo",
          lastName: "bar",
          hasPiRole: false,
          hasSysAdminRole: false,
          email: null,
          workbenchId: 1,
          _links: [],
        });
        jest
          .spyOn(peopleStore, "fetchCurrentUsersGroups")
          .mockImplementation(() => Promise.resolve(groups));

        const container = await searchStore.createNewContainer();
        expect(container.sharedWith).not.toBe(null);
        if (!container.sharedWith)
          throw new Error("This can't throw, but Flow needs the check");
        const sharedWith = container.sharedWith;
        expect(sharedWith.map(({ group }) => group)).toEqual(groups);
        expect(sharedWith.every(({ itemOwnerGroup }) => itemOwnerGroup)).toBe(
          true
        );
      })
    );
  });

  test("Should not call fetchAdditionalInfo on the new container.", async () => {
    const { searchStore } = getRootStore();
    const spy = jest.spyOn(ContainerModel.prototype, "fetchAdditionalInfo");
    await searchStore.createNewContainer();
    expect(spy).not.toHaveBeenCalled();
  });
});
