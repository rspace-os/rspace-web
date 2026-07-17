import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { getWorkspaceRecordInformationAjax } from "@/modules/workspace/queries";
import type { WorkspaceRecordInformation } from "@/modules/workspace/schema";

const WORKSPACE_API_BASE_URL = "/workspace";

const mockRecordInformation: WorkspaceRecordInformation = {
  id: 21,
  oid: {
    idString: "GL21",
  },
  name: "Gallery image",
  type: "Image",
  ownerFullName: "Alice Example",
  ownerUsername: "alice",
  description: "Microscope image",
  signatureStatus: "UNSIGNABLE",
  extension: "png",
  size: 1024,
  version: 2,
  thumbnailId: 301,
  widthResized: 640,
  heightResized: 480,
  modificationDate: 1712755200000,
  creationDateWithClientTimezoneOffset: "2026-04-14 10:00 +0000",
  modificationDateWithClientTimezoneOffset: "2026-04-14 10:05 +0000",
};

describe("getWorkspaceRecordInformationAjax", () => {
  it("fetches and unwraps detailed record information", async () => {
    const requests: Request[] = [];
    server.use(
      http.get(`${WORKSPACE_API_BASE_URL}/getRecordInformation`, ({ request }) => {
        requests.push(request);
        return HttpResponse.json({
          data: mockRecordInformation,
          error: null,
          errorMsg: null,
          success: true,
        });
      }),
    );

    const result = await getWorkspaceRecordInformationAjax({
      recordId: 21,
      revision: 3,
      version: 2,
    });

    expect(result).toEqual(mockRecordInformation);
    expect(requests).toHaveLength(1);
    const requestUrl = new URL(requests[0].url);
    expect(Object.fromEntries(requestUrl.searchParams)).toEqual({ recordId: "21", revision: "3", version: "2" });
    expect(requests[0].headers.get("X-Requested-With")).toBe("XMLHttpRequest");
  });

  it("throws the endpoint error message when a successful response has no data", async () => {
    server.use(
      http.get(`${WORKSPACE_API_BASE_URL}/getRecordInformation`, () =>
        HttpResponse.json({
          data: null,
          error: {
            errorMessages: [
              {
                defaultMessage: "Could not load record information",
                errorCode: "workspace.getRecordInformation.failed",
                field: "recordId",
              },
            ],
          },
          success: false,
        }),
      ),
    );

    await expect(getWorkspaceRecordInformationAjax({ recordId: 21 })).rejects.toThrow(
      "Could not load record information",
    );
  });

  it("throws endpoint error details for non-OK responses", async () => {
    server.use(
      http.get(`${WORKSPACE_API_BASE_URL}/getRecordInformation`, () =>
        HttpResponse.json(
          {
            data: null,
            error: {
              errorMessages: ["Record not found"],
            },
            success: false,
          },
          {
            status: 404,
            statusText: "Not Found",
          },
        ),
      ),
    );

    await expect(getWorkspaceRecordInformationAjax({ recordId: 99 })).rejects.toThrow("Record not found");
  });
});
