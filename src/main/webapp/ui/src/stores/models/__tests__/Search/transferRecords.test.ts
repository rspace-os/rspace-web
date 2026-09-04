import { beforeEach, describe, expect, test, vi } from "vitest";
import InvApiService from "../../../../common/InvApiService";
import { mockIGSNAttrs } from "../../../../Inventory/components/Fields/Identifiers/__tests__/mocking";
import type { ExternalMetadataUpdate, IdentifierAttrs } from "../../../definitions/Identifier";
import AlwaysNewFactory from "../../Factory/AlwaysNewFactory";
import Search from "../../Search";
import { instrumentAttrs, makeMockInstrument } from "../InstrumentModel/mocking";

const { mockAddAlert } = vi.hoisted(() => ({ mockAddAlert: vi.fn() }));

vi.mock("../../../../common/InvApiService", () => ({
  default: {
    bulk: vi.fn(),
    query: vi.fn(),
    get: vi.fn(),
  },
}));

vi.mock("../../../stores/getRootStore", () => ({
  default: () => ({
    authStore: { isSynchronizing: false },
    trackingStore: { trackEvent: vi.fn() },
    peopleStore: { getUser: vi.fn().mockResolvedValue(null), currentUser: null },
    uiStore: {
      addAlert: mockAddAlert,
      removeAlert: vi.fn(),
      setVisiblePanel: vi.fn(),
      setDirty: vi.fn(),
      setPageNavigationConfirmation: vi.fn(),
    },
    searchStore: {
      activeResult: null,
      getInstrumentTemplate: vi.fn(),
      search: { replaceResult: vi.fn(), activeResult: null },
    },
  }),
}));

vi.mock("../../../stores/SearchStore", () => ({ default: class {} })); // break import cycle

const REASON =
  "Could not update the instrument metadata held by DataCite. The instrument itself was saved, so saving it again will try the update once more.";

const pidinst = (update: ExternalMetadataUpdate): IdentifierAttrs => ({
  ...mockIGSNAttrs(),
  doiType: "PIDINST_DATACITE",
  state: "draft",
  externalMetadataUpdate: update,
});

function mockBulkTransferReturning(identifiers: Array<IdentifierAttrs>) {
  vi.mocked(InvApiService.bulk).mockResolvedValueOnce({
    data: {
      results: [{ error: null, record: instrumentAttrs({ id: 1, globalId: "IN1", identifiers }) }],
      errorCount: 0,
    },
    status: 200,
    statusText: "OK",
    headers: {},
    // biome-ignore lint/suspicious/noExplicitAny: test setup
    config: {} as any,
  });
}

async function transfer(): Promise<void> {
  const search = new Search({ factory: new AlwaysNewFactory() });
  // The post-transfer refresh chain (bench count, re-search, active result) is not under test.
  vi.spyOn(search, "updateStateAfterTransfer").mockResolvedValue(undefined);
  await search.transferRecords("newowner", [makeMockInstrument({ id: 1, globalId: "IN1", templateId: null })]);
}

/*
 * The web UI transfers through the bulk CHANGE_OWNER endpoint, which pushes the new owner to the
 * provider once its transaction commits (ADR 0008) and decorates the returned records with the
 * outcome. The first "notice" alert in each sequence is the in-progress toast the transfer shows
 * while the request is pending; the "success" is the transferred toast.
 */
describe("Search.transferRecords surfaces the external PIDINST update outcome", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test("a failed push on a transferred instrument is shown as an error after the transferred toast", async () => {
    mockBulkTransferReturning([pidinst({ succeeded: false, outcome: "FAILED", reason: REASON })]);

    await transfer();

    expect(mockAddAlert.mock.calls.map(([alert]) => alert.variant)).toEqual(["notice", "success", "error"]);
    expect(mockAddAlert).toHaveBeenCalledWith(expect.objectContaining({ variant: "error", message: REASON }));
  });

  test("the departing owner's blanked identifier list is silence, not failure", async () => {
    mockBulkTransferReturning([]);

    await transfer();

    expect(mockAddAlert.mock.calls.map(([alert]) => alert.variant)).toEqual(["notice", "success"]);
  });
});
