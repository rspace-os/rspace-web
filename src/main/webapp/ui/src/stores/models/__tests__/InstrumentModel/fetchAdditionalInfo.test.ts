import { beforeEach, describe, expect, test, vi } from "vitest";
import InvApiService from "../../../../common/InvApiService";
import { instrumentTemplateAttrs } from "../InstrumentTemplateModel/mocking";
import { instrumentAttrs, makeMockInstrument } from "./mocking";

const mockRootStore = {
  trackingStore: {
    trackEvent: vi.fn(),
  },
  uiStore: {
    addAlert: vi.fn(),
  },
};

vi.mock("../../../../common/InvApiService", () => ({
  default: {
    query: vi.fn(),
    get: vi.fn(),
  },
}));
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => mockRootStore,
}));

describe("InstrumentModel.fetchAdditionalInfo", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  test("resolves for an instrument without a templateId", async () => {
    const instrument = makeMockInstrument({ templateId: null });
    vi.spyOn(InvApiService, "query").mockResolvedValue({
      data: instrumentAttrs(),
      status: 200,
      statusText: "OK",
      headers: {},
      // biome-ignore lint/suspicious/noExplicitAny: test setup
      config: {} as any,
    });
    await expect(instrument.fetchAdditionalInfo()).resolves.toBeUndefined();
    expect(instrument.template).toBeNull();
  });

  test("fetches and sets the template when templateId is present", async () => {
    const instrument = makeMockInstrument({ id: 1, templateId: 10 });
    const templateData = instrumentTemplateAttrs({ id: 10, globalId: "NT10" });

    vi.spyOn(InvApiService, "query").mockResolvedValue({
      data: instrumentAttrs({ templateId: 10 }),
      status: 200,
      statusText: "OK",
      headers: {},
      // biome-ignore lint/suspicious/noExplicitAny: test setup
      config: {} as any,
    });
    vi.spyOn(InvApiService, "get").mockResolvedValue({
      data: templateData,
      status: 200,
      statusText: "OK",
      headers: {},
      // biome-ignore lint/suspicious/noExplicitAny: test setup
      config: {} as any,
    });

    await instrument.fetchAdditionalInfo();
    expect(instrument.template).not.toBeNull();
    expect(instrument.template?.id).toBe(10);
  });

  test("tracks an inventory access event", async () => {
    const instrument = makeMockInstrument({ id: 1, templateId: null });
    vi.spyOn(InvApiService, "query").mockResolvedValue({
      data: instrumentAttrs(),
      status: 200,
      statusText: "OK",
      headers: {},
      // biome-ignore lint/suspicious/noExplicitAny: test setup
      config: {} as any,
    });
    mockRootStore.trackingStore.trackEvent.mockClear();

    await instrument.fetchAdditionalInfo();

    expect(mockRootStore.trackingStore.trackEvent).toHaveBeenCalledWith(
      "InventoryRecordAccessed",
      expect.objectContaining({ type: "instrument" }),
    );
  });

  test("a second call while a base fetch is in-progress awaits the shared query promise", async () => {
    const instrument = makeMockInstrument({ id: 1, templateId: null });
    // Both calls share the same ApiService.query promise via fetchingAdditionalInfo.
    // The second call detects it and awaits the same promise rather than starting a new request.
    let resolveQuery!: (v: unknown) => void;
    const queryPromise = new Promise((res) => {
      resolveQuery = res;
    });
    vi.spyOn(InvApiService, "query").mockReturnValue(queryPromise as ReturnType<typeof InvApiService.query>);

    const first = instrument.fetchAdditionalInfo();
    const second = instrument.fetchAdditionalInfo();

    // Resolve the shared query
    resolveQuery({ data: instrumentAttrs(), status: 200, statusText: "OK", headers: {}, config: {} });

    await Promise.all([first, second]);
    // ApiService.query was only called once (second call reused the in-flight promise)
    expect(InvApiService.query).toHaveBeenCalledOnce();
  });
});
