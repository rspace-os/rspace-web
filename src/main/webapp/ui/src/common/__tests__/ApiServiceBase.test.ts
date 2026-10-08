import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import ApiServiceBase from "@/common/ApiServiceBase";
import axios, { type AxiosError } from "@/common/axios";

const mockRootStore = vi.hoisted(() => ({
  authStore: { authenticate: vi.fn(), isAuthenticated: true },
  uiStore: { addAlert: vi.fn(), removeAlert: vi.fn() },
}));

vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => mockRootStore,
}));

beforeEach(() => {
  vi.clearAllMocks();
});

describe("ApiServiceBase token-mint failures", () => {
  it.each([
    "https://example.test/api/v2/oauth/tokens",
    "https://example.test/public/publicView/userform/ajax/inventoryOauthToken?format=json",
  ])("does not recursively retry a 401 from %s", async (requestUrl) => {
    const service = new ApiServiceBase("/api/v1/");
    const error = {
      config: { url: requestUrl, headers: {} },
      response: { status: 401 },
      request: { responseURL: requestUrl },
    } as AxiosError;

    await expect(service.on401Retry(error)).rejects.toBe(error);

    expect(mockRootStore.authStore.authenticate).not.toHaveBeenCalled();
    expect(mockRootStore.uiStore.addAlert).toHaveBeenCalledOnce();
  });

  it("retries a read only once without adding a request body", async () => {
    let requests = 0;
    const bodies: string[] = [];
    const urls: string[] = [];
    server.use(
      http.get("/api/v1/retry-test", async ({ request }) => {
        requests += 1;
        urls.push(new URL(request.url).pathname + new URL(request.url).search);
        bodies.push(await request.text());
        return new HttpResponse(null, { status: 401 });
      }),
    );
    const service = new ApiServiceBase("/api/v1/");

    await expect(
      service.api.get("retry-test", { params: new URLSearchParams({ query: "sample", pageNumber: "1" }) }),
    ).rejects.toMatchObject({ response: { status: 401 } });

    expect(requests).toBe(2);
    expect(bodies).toEqual(["", ""]);
    expect(urls).toEqual([
      "/api/v1/retry-test?query=sample&pageNumber=1",
      "/api/v1/retry-test?query=sample&pageNumber=1",
    ]);
    expect(mockRootStore.authStore.authenticate).toHaveBeenCalledOnce();
  });

  it("preserves the URL of a global Axios read when the service has a different base URL", async () => {
    let requests = 0;
    server.use(
      http.get("/api/v1/global-retry-test", () => {
        requests += 1;
        return new HttpResponse(null, { status: 401 });
      }),
    );
    new ApiServiceBase("/api/inventory/v1/");

    await expect(axios.get("/api/v1/global-retry-test")).rejects.toMatchObject({ response: { status: 401 } });

    expect(requests).toBe(2);
    expect(mockRootStore.authStore.authenticate).toHaveBeenCalledOnce();
  });

  it("preserves a rejected mutation payload without replaying the mutation", async () => {
    const service = new ApiServiceBase("/api/v1/");
    const payload = { name: "instrument" };
    const error = {
      config: { url: "/api/v1/instruments", data: payload, headers: {} },
      response: { status: 401 },
    } as AxiosError;

    await expect(service.on401Retry(error)).rejects.toBe(error);

    expect(error.config?.data).toBe(payload);
    expect(mockRootStore.authStore.authenticate).not.toHaveBeenCalled();
  });
});
