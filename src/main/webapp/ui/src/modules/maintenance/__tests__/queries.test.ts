import { HttpResponse, http } from "msw";
import { describe, expect, it, onTestFinished, vi } from "vitest";
import { v2ListEnvelope } from "@/__tests__/helpers/v2ListEnvelope";
import { server } from "@/__tests__/mswServer";
import type { ListMaintenances200 } from "@/modules/common/api/v2/generated/models/listMaintenances200";
import { setApiV2TokenProvider } from "@/modules/common/api/v2/tokenProvider";
import { getMaintenanceStatus } from "@/modules/maintenance/queries";

describe("getMaintenanceStatus", () => {
  it("reports 'in-progress' while maintenance is active", async () => {
    let capturedRequest: Request | undefined;
    server.use(
      http.get("/api/v2/maintenances", ({ request }) => {
        capturedRequest = request;
        return HttpResponse.json(v2ListEnvelope([{ id: 1, canUserLoginNow: false }]));
      }),
    );

    expect(await getMaintenanceStatus()).toBe("in-progress");
    const requestUrl = new URL(capturedRequest?.url ?? "");
    expect(requestUrl.searchParams.get("limit")).toBe("1");
    expect(requestUrl.searchParams.get("fields[maintenances]")).toBe("canUserLoginNow");
    expect(capturedRequest?.headers.get("Authorization")).toBeNull();
    expect(capturedRequest?.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
  });

  it.each([
    {
      name: "reports 'clear' once maintenance is over",
      response: () => HttpResponse.json(v2ListEnvelope([{ id: 1, canUserLoginNow: true }])),
      expected: "clear",
    },
    {
      name: "reports 'clear' when no maintenance window remains",
      response: () => HttpResponse.json(v2ListEnvelope([])),
      expected: "clear",
    },
    {
      name: "treats a failed status check as still in maintenance (no false redirect)",
      response: () => new HttpResponse(null, { status: 503 }),
      expected: "in-progress",
    },
    {
      name: "treats a missing projected login status as still in maintenance",
      response: () => HttpResponse.json(v2ListEnvelope([{ id: 1 }])),
      expected: "in-progress",
    },
    {
      name: "treats a malformed response as still in maintenance",
      response: () => HttpResponse.json({ unexpected: true }),
      expected: "in-progress",
    },
  ] as const)("$name", async ({ response, expected }) => {
    server.use(http.get("/api/v2/maintenances", response));
    expect(await getMaintenanceStatus()).toBe(expected);
  });
});

it("reads public maintenance status without an authenticated browser session", async () => {
  const getToken = vi.fn(async () => {
    throw new TypeError("No authenticated session");
  });
  const recover = vi.fn(async () => {
    throw new TypeError("No authenticated session");
  });
  onTestFinished(setApiV2TokenProvider({ getToken, recover }));
  server.use(
    http.get("/api/v2/maintenances", () =>
      HttpResponse.json<ListMaintenances200>({
        docs: [{ id: 1, canUserLoginNow: true }],
        page: 1,
        limit: 1,
      }),
    ),
  );
  await expect(getMaintenanceStatus()).resolves.toBe("clear");
  expect(getToken).not.toHaveBeenCalled();
  expect(recover).not.toHaveBeenCalled();
});
