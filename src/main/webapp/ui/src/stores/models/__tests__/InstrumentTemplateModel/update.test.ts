import { runInAction } from "mobx";
import { beforeEach, describe, expect, test, vi } from "vitest";
import InvApiService from "../../../../common/InvApiService";
import { instrumentTemplateAttrs, makeMockInstrumentTemplate } from "./mocking";

const { mockAddAlert, mockGetInstrumentTemplate, mockSetActiveResult, mockReplaceResult } = vi.hoisted(() => ({
  mockAddAlert: vi.fn(),
  mockGetInstrumentTemplate: vi.fn(),
  mockSetActiveResult: vi.fn(),
  mockReplaceResult: vi.fn(),
}));

vi.mock("../../../../common/InvApiService", () => ({
  default: {
    query: vi.fn(),
    get: vi.fn(),
    update: vi.fn(),
  },
}));

vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    authStore: { isSynchronizing: false },
    trackingStore: { trackEvent: vi.fn() },
    uiStore: {
      addAlert: mockAddAlert,
      setPageNavigationConfirmation: vi.fn(),
      setDirty: vi.fn(),
    },
    searchStore: {
      getInstrumentTemplate: mockGetInstrumentTemplate,
      search: {
        replaceResult: mockReplaceResult,
        setActiveResult: mockSetActiveResult,
        activeResult: null,
      },
    },
  }),
}));

function mockPutReturningVersion(version: number) {
  vi.mocked(InvApiService.update).mockResolvedValueOnce({
    data: instrumentTemplateAttrs({ id: 5, version }),
    status: 200,
    statusText: "OK",
    headers: {},
    // biome-ignore lint/suspicious/noExplicitAny: test setup
    config: {} as any,
  });
}

describe("InstrumentTemplateModel.update", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test("shows update toast when version bumps and current user has instruments", async () => {
    const template = makeMockInstrumentTemplate({ id: 5, version: 1 });
    mockPutReturningVersion(2);

    const latestTemplate = makeMockInstrumentTemplate({ id: 5, version: 2 });
    mockGetInstrumentTemplate.mockResolvedValueOnce(latestTemplate);
    mockSetActiveResult.mockResolvedValueOnce(undefined);

    vi.spyOn(template.search.fetcher, "performInitialSearch").mockImplementationOnce(async () => {
      runInAction(() => {
        (template.search.fetcher as unknown as { results: unknown[] }).results = [{ owner: { isCurrentUser: true } }];
      });
    });
    vi.spyOn(latestTemplate, "addScopedToast").mockImplementation(() => {});

    await template.update();

    expect(mockAddAlert).toHaveBeenCalledWith(expect.objectContaining({ variant: "notice" }));
  });

  test("does not show toast when the version does not change", async () => {
    const template = makeMockInstrumentTemplate({ id: 5, version: 1 });
    mockPutReturningVersion(1);

    const latestTemplate = makeMockInstrumentTemplate({ id: 5, version: 1 });
    mockGetInstrumentTemplate.mockResolvedValueOnce(latestTemplate);
    mockSetActiveResult.mockResolvedValueOnce(undefined);

    vi.spyOn(template.search.fetcher, "performInitialSearch").mockImplementationOnce(async () => {
      runInAction(() => {
        (template.search.fetcher as unknown as { results: unknown[] }).results = [{ owner: { isCurrentUser: true } }];
      });
    });

    await template.update();

    expect(mockAddAlert).not.toHaveBeenCalledWith(expect.objectContaining({ variant: "notice" }));
  });

  test("does not show toast when no instruments are owned by the current user", async () => {
    const template = makeMockInstrumentTemplate({ id: 5, version: 1 });
    mockPutReturningVersion(2);

    const latestTemplate = makeMockInstrumentTemplate({ id: 5, version: 2 });
    mockGetInstrumentTemplate.mockResolvedValueOnce(latestTemplate);
    mockSetActiveResult.mockResolvedValueOnce(undefined);

    vi.spyOn(template.search.fetcher, "performInitialSearch").mockImplementationOnce(async () => {
      runInAction(() => {
        (template.search.fetcher as unknown as { results: unknown[] }).results = [];
      });
    });

    await template.update();

    expect(mockAddAlert).not.toHaveBeenCalledWith(expect.objectContaining({ variant: "notice" }));
  });
});
