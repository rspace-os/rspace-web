/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */

import InvApiService from "../../../../common/InvApiService";
import { makeMockTemplate, templateAttrs } from "./mocking";

jest.mock("../../../../common/InvApiService", () => ({
  query: jest.fn(),
  post: jest.fn(),
  delete: jest.fn(),
  get: jest.fn(),
}));

describe("method: setEditing", () => {
  test("Properties should be reset.", async () => {
    jest.spyOn(InvApiService, "get").mockImplementation(() =>
      Promise.resolve({
        data: templateAttrs({
          name: "oldName",
        }),
      })
    );
    jest.spyOn(InvApiService, "post").mockImplementation(() =>
      Promise.resolve({
        data: {
          status: "LOCKED_OK",
          remainingTimeInSeconds: 500,
          lockOwner: {
            firstName: "Joe",
            lastName: "Bloggs",
            username: "jbloggs",
          },
        },
      })
    );
    jest.spyOn(InvApiService, "delete").mockImplementation(() =>
      Promise.resolve({
        data: {
          status: "UNLOCKED_OK",
          remainingTimeInSeconds: 500,
          lockOwner: {
            firstName: "Joe",
            lastName: "Bloggs",
            username: "jbloggs",
          },
        },
        status: 200,
      })
    );
    const template = makeMockTemplate();
    await template.setEditing(true, null, false);
    template.setAttributesDirty({
      name: "newName",
    });
    expect(template.name).toBe("newName");
    await template.setEditing(false); // the are-you-sure dialog actually calls this, not `cancel`
    expect(template.name).toBe("oldName");
  });
});
