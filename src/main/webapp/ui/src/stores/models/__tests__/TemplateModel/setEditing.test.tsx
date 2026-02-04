
import { describe, expect, test, vi } from 'vitest';
import InvApiService from "../../../../common/InvApiService";
import { makeMockTemplate, templateAttrs } from "./mocking";
import { AxiosResponse } from "axios";

vi.mock("../../../../common/InvApiService", () => ({
  default: {
  query: vi.fn(),
  post: vi.fn(),
  delete: vi.fn(),
  get: vi.fn(),
  }}));
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

    vi.spyOn(InvApiService, "get").mockResolvedValue(getMockResponse);
    vi.spyOn(InvApiService, "post").mockResolvedValue(postMockResponse);
    vi.spyOn(InvApiService, "delete").mockResolvedValue(deleteMockResponse);
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

