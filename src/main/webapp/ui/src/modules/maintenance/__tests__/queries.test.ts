import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { getMaintenanceStatus } from "@/modules/maintenance/queries";

describe("getMaintenanceStatus", () => {
  it("reports 'in-progress' while maintenance is active", async () => {
    let requestHeaders: Headers | undefined;
    server.use(
      http.get("/public/maintenanceStatus", ({ request }) => {
        requestHeaders = request.headers;
        return new HttpResponse("Maintenance in progress");
      }),
    );

    expect(await getMaintenanceStatus()).toBe("in-progress");
    expect(requestHeaders?.get("X-Requested-With")).toBe("XMLHttpRequest");
  });

  it("reports 'clear' once maintenance is over", async () => {
    server.use(http.get("/public/maintenanceStatus", () => new HttpResponse("No maintenance")));
    expect(await getMaintenanceStatus()).toBe("clear");
  });

  it("treats a failed status check as still in maintenance (no false redirect)", async () => {
    server.use(http.get("/public/maintenanceStatus", () => new HttpResponse(null, { status: 503 })));
    expect(await getMaintenanceStatus()).toBe("in-progress");
  });
});
