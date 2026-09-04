import { beforeEach, describe, expect, test, vi } from "vitest";
import InvApiService from "../../../../common/InvApiService";
import { mockIGSNAttrs } from "../../../../Inventory/components/Fields/Identifiers/__tests__/mocking";
import type { ExternalMetadataUpdate } from "../../../definitions/Identifier";
import { instrumentAttrs, makeMockInstrument } from "./mocking";

const { mockAddAlert } = vi.hoisted(() => ({ mockAddAlert: vi.fn() }));

vi.mock("../../../../common/InvApiService", () => ({
  default: {
    query: vi.fn(),
    get: vi.fn(),
    post: vi.fn(),
    update: vi.fn(),
  },
}));

vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    authStore: { isSynchronizing: false },
    trackingStore: { trackEvent: vi.fn() },
    uiStore: {
      addAlert: mockAddAlert,
      setDirty: vi.fn(),
      setPageNavigationConfirmation: vi.fn(),
    },
    searchStore: {
      getInstrumentTemplate: vi.fn(),
      search: { replaceResult: vi.fn(), activeResult: null },
    },
  }),
}));

const REASON =
  "Could not update the instrument metadata held by B2INST. The instrument itself was saved, so saving it again will try the update once more.";

function mockPutReturningIdentifierWith(update?: ExternalMetadataUpdate) {
  vi.mocked(InvApiService.update).mockResolvedValueOnce({
    data: instrumentAttrs({
      id: 1,
      globalId: "IN1",
      identifiers: [{ ...mockIGSNAttrs(), doiType: "PIDINST_B2INST", state: "draft", externalMetadataUpdate: update }],
    }),
    status: 200,
    statusText: "OK",
    headers: {},
    // biome-ignore lint/suspicious/noExplicitAny: test setup
    config: {} as any,
  });
}

async function saveInstrument(): Promise<void> {
  const instrument = makeMockInstrument({ id: 1, globalId: "IN1", templateId: null });
  // Leaving edit mode releases the lock and refetches the record over the API; not under test.
  vi.spyOn(instrument, "setEditing").mockResolvedValue("UNLOCKED_OK");
  await instrument.update();
}

/*
 * The save response decorates each identifier with the outcome of the external PIDINST update
 * (RSDEV-1251). RSDEV-1356: the user is told about it, after the ordinary "saved" toast.
 */
describe("InstrumentModel.update surfaces the external PIDINST update outcome", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test("a failed push is shown as an error after the saved toast, with the server's reason", async () => {
    mockPutReturningIdentifierWith({ succeeded: false, outcome: "FAILED", reason: REASON });

    await saveInstrument();

    expect(mockAddAlert.mock.calls.map(([alert]) => alert.variant)).toEqual(["success", "error"]);
    expect(mockAddAlert).toHaveBeenCalledWith(expect.objectContaining({ variant: "error", message: REASON }));
  });

  test("a record frozen by its own state is shown as a notice", async () => {
    mockPutReturningIdentifierWith({ succeeded: false, outcome: "NOT_UPDATABLE", reason: REASON });

    await saveInstrument();

    expect(mockAddAlert).toHaveBeenCalledWith(expect.objectContaining({ variant: "notice", message: REASON }));
  });

  test("a successful push adds nothing beyond the ordinary saved toast", async () => {
    mockPutReturningIdentifierWith({
      succeeded: true,
      outcome: "UPDATED",
      reason: "The instrument metadata held by B2INST was updated.",
    });

    await saveInstrument();

    expect(mockAddAlert).toHaveBeenCalledTimes(1);
    expect(mockAddAlert).toHaveBeenCalledWith(expect.objectContaining({ variant: "success" }));
  });

  test("an identifier with no outcome behaves exactly as before", async () => {
    mockPutReturningIdentifierWith(undefined);

    await saveInstrument();

    expect(mockAddAlert).toHaveBeenCalledTimes(1);
    expect(mockAddAlert).toHaveBeenCalledWith(expect.objectContaining({ variant: "success" }));
  });
});
