import { beforeEach, describe, expect, test, vi } from "vitest";
import InvApiService from "../../../../common/InvApiService";
import { instrumentTemplateAttrs, makeMockInstrumentTemplate } from "./mocking";

const mockTrackEvent = vi.fn();

vi.mock("../../../../common/InvApiService", () => ({
  default: {
    query: vi.fn(),
  },
}));
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    trackingStore: { trackEvent: mockTrackEvent },
    uiStore: { addAlert: vi.fn() },
  }),
}));

describe("InstrumentTemplateModel.fetchAdditionalInfo", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test("resolves without error for a saved template", async () => {
    const template = makeMockInstrumentTemplate({ id: 1 });
    vi.spyOn(InvApiService, "query").mockResolvedValue({
      data: instrumentTemplateAttrs(),
      status: 200,
      statusText: "OK",
      headers: {},
      // biome-ignore lint/suspicious/noExplicitAny: test setup
      config: {} as any,
    });
    await expect(template.fetchAdditionalInfo()).resolves.toBeUndefined();
  });

  test("tracks an InventoryRecordAccessed event with type 'instrumentTemplate'", async () => {
    const template = makeMockInstrumentTemplate({ id: 1 });
    vi.spyOn(InvApiService, "query").mockResolvedValue({
      data: instrumentTemplateAttrs(),
      status: 200,
      statusText: "OK",
      headers: {},
      // biome-ignore lint/suspicious/noExplicitAny: test setup
      config: {} as any,
    });
    await template.fetchAdditionalInfo();
    expect(mockTrackEvent).toHaveBeenCalledWith(
      "InventoryRecordAccessed",
      expect.objectContaining({ type: "instrumentTemplate" }),
    );
  });

  test("a second call while the base fetch is in-progress reuses the in-flight query", async () => {
    const template = makeMockInstrumentTemplate({ id: 1 });
    let resolveQuery!: (v: unknown) => void;
    const queryPromise = new Promise((res) => {
      resolveQuery = res;
    });
    vi.spyOn(InvApiService, "query").mockReturnValue(queryPromise as ReturnType<typeof InvApiService.query>);

    const first = template.fetchAdditionalInfo();
    const second = template.fetchAdditionalInfo();

    resolveQuery({ data: instrumentTemplateAttrs(), status: 200, statusText: "OK", headers: {}, config: {} });

    await Promise.all([first, second]);
    expect(InvApiService.query).toHaveBeenCalledOnce();
  });

  test("returns immediately (no query) when id is null", async () => {
    const template = makeMockInstrumentTemplate({ id: null, globalId: null });
    vi.spyOn(InvApiService, "query").mockResolvedValue({
      data: instrumentTemplateAttrs(),
      status: 200,
      statusText: "OK",
      headers: {},
      // biome-ignore lint/suspicious/noExplicitAny: test setup
      config: {} as any,
    });
    await template.fetchAdditionalInfo();
    expect(InvApiService.query).not.toHaveBeenCalled();
  });
});
