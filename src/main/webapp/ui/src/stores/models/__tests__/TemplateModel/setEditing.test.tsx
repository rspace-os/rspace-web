/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import InvApiService from "../../../../common/InvApiService";
import { makeMockTemplate, templateAttrs } from "./mocking";
import { AxiosResponse } from "axios";

jest.mock("../../../../common/InvApiService", () => ({
  query: jest.fn(),
  post: jest.fn(),
  delete: jest.fn(),
  get: jest.fn(),
}));

describe("method: setEditing", () => {
  test("Properties should be reset.", async () => {
    // Create properly typed mock responses
    const getMockResponse = {
      data: templateAttrs({
        name: "oldName",
      }),
      status: 200,
      statusText: "OK",
      headers: {},
      config: { headers: {} },
    } as AxiosResponse;

    const postMockResponse = {
      data: {
        status: "LOCKED_OK",
        remainingTimeInSeconds: 500,
        lockOwner: {
          firstName: "Joe",
          lastName: "Bloggs",
          username: "jbloggs",
        },
      },
      status: 200,
      statusText: "OK",
      headers: {},
      config: { headers: {} },
    } as AxiosResponse;

    const deleteMockResponse = {
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
      statusText: "OK",
      headers: {},
      config: { headers: {} },
    } as AxiosResponse;

    // Setup the mock implementations
    jest.spyOn(InvApiService, "get").mockResolvedValue(getMockResponse);
    jest.spyOn(InvApiService, "post").mockResolvedValue(postMockResponse);
    jest.spyOn(InvApiService, "delete").mockResolvedValue(deleteMockResponse);

    const template = makeMockTemplate();
    await template.setEditing(true, undefined, false);
    template.setAttributesDirty({
      name: "newName",
    });
    expect(template.name).toBe("newName");
    await template.setEditing(false); // the are-you-sure dialog actually calls this, not `cancel`
    expect(template.name).toBe("oldName");
  });
});
