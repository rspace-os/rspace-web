import { beforeEach, describe, expect, test, vi } from "vitest";
import InvApiService from "../../../../common/InvApiService";
import { mockIGSNAttrs } from "../../../../Inventory/components/Fields/Identifiers/__tests__/mocking";
import { makeMockInstrument } from "./mocking";

const mockRootStore = {
  trackingStore: { trackEvent: vi.fn() },
  uiStore: {
    addAlert: vi.fn(),
    confirm: vi.fn(),
    setDirty: vi.fn(),
    setPageNavigationConfirmation: vi.fn(),
  },
  searchStore: {
    getInstrumentTemplate: vi.fn(),
    search: { replaceResult: vi.fn() },
  },
};

vi.mock("../../../../common/InvApiService", () => ({
  default: {
    query: vi.fn(),
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}));
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => mockRootStore,
}));

/**
 * Registering a PIDINST writes the identifier's public landing page into the instrument's Landing
 * page field server-side, and deleting it clears that field again (ADR 0006 items 4 and 5). Neither
 * value arrives with the identifier response, so without a refetch the user is left looking at a
 * stale field: blank for a record that now has a landing page, or still populated for one that no
 * longer does.
 *
 * That is worse than cosmetic. A user who types into the apparently-empty field is overwriting a
 * value the backend has just registered with a PID provider, which is precisely the drift ADR 0006
 * exists to prevent.
 */
describe("identifier changes refetch the record", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockRootStore.uiStore.confirm.mockResolvedValue(true);
  });

  test("addIdentifier refetches after minting, so the written Landing page is shown", async () => {
    const instrument = makeMockInstrument({ id: 1, globalId: "IN1", templateId: null });
    const refetch = vi.spyOn(instrument, "fetchAdditionalInfo").mockResolvedValue(undefined);
    vi.spyOn(InvApiService, "post").mockResolvedValue({
      data: mockIGSNAttrs(),
      status: 201,
      statusText: "Created",
      headers: {},
      // biome-ignore lint/suspicious/noExplicitAny: test setup
      config: {} as any,
    });

    await instrument.addIdentifier();

    expect(refetch).toHaveBeenCalled();
  });

  test("removeIdentifier refetches after deleting, so the cleared Landing page is shown", async () => {
    const instrument = makeMockInstrument({
      id: 1,
      globalId: "IN1",
      templateId: null,
      identifiers: [mockIGSNAttrs()],
    });
    const refetch = vi.spyOn(instrument, "fetchAdditionalInfo").mockResolvedValue(undefined);
    vi.spyOn(InvApiService, "delete").mockResolvedValue({
      data: true,
      status: 200,
      statusText: "OK",
      headers: {},
      // biome-ignore lint/suspicious/noExplicitAny: test setup
      config: {} as any,
    });

    await instrument.removeIdentifier(instrument.identifiers[0].id);

    expect(refetch).toHaveBeenCalled();
  });

  test("a declined confirmation neither posts nor refetches", async () => {
    const instrument = makeMockInstrument({ id: 1, globalId: "IN1", templateId: null });
    const refetch = vi.spyOn(instrument, "fetchAdditionalInfo").mockResolvedValue(undefined);
    mockRootStore.uiStore.confirm.mockResolvedValue(false);

    await instrument.addIdentifier();

    expect(InvApiService.post).not.toHaveBeenCalled();
    expect(refetch).not.toHaveBeenCalled();
  });
});
