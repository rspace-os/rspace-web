import { beforeEach, describe, expect, test, vi } from "vitest";
import i18n from "@/modules/common/i18n";
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

const pidinst = (update: ExternalMetadataUpdate, doi?: string): IdentifierAttrs => ({
  ...mockIGSNAttrs(),
  doiType: "PIDINST_DATACITE",
  state: "draft",
  ...(doi ? { doi } : {}),
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

function mockBulkTransferReturningMany(count: number, identifiers: Array<IdentifierAttrs>) {
  vi.mocked(InvApiService.bulk).mockResolvedValueOnce({
    data: {
      results: Array.from({ length: count }, (_, i) => ({
        error: null,
        record: instrumentAttrs({ id: i + 1, globalId: `IN${i + 1}`, identifiers }),
      })),
      errorCount: 0,
    },
    status: 200,
    statusText: "OK",
    headers: {},
    // biome-ignore lint/suspicious/noExplicitAny: test setup
    config: {} as any,
  });
}

async function transferMany(count: number): Promise<void> {
  const search = new Search({ factory: new AlwaysNewFactory() });
  vi.spyOn(search, "updateStateAfterTransfer").mockResolvedValue(undefined);
  await search.transferRecords(
    "newowner",
    Array.from({ length: count }, (_, i) =>
      makeMockInstrument({ id: i + 1, globalId: `IN${i + 1}`, templateId: null }),
    ),
  );
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
    mockBulkTransferReturning([pidinst({ outcome: "FAILED", reason: REASON })]);

    await transfer();

    expect(mockAddAlert.mock.calls.map(([alert]) => alert.variant)).toEqual(["notice", "success", "error"]);
    expect(mockAddAlert).toHaveBeenCalledWith(expect.objectContaining({ variant: "error", message: REASON }));
  });

  /*
   * Both variants wait to be dismissed, so one toast per identifier would leave a provider outage
   * across a bulk transfer as one sticky toast per instrument to clear by hand. The outcomes are
   * still all reported, as detail rows of a single alert, the way this method reports its own
   * bulk successes and failures.
   */
  test("a provider outage across many transferred instruments is one alert, not one per instrument", async () => {
    mockBulkTransferReturningMany(3, [pidinst({ outcome: "FAILED", reason: REASON })]);

    await transferMany(3);

    expect(mockAddAlert.mock.calls.map(([alert]) => alert.variant)).toEqual(["notice", "success", "error"]);
    const error = mockAddAlert.mock.calls.map(([alert]) => alert).find((alert) => alert.variant === "error");
    expect(error.details).toHaveLength(3);
    expect(error.details.map((d: { help: string }) => d.help)).toEqual([REASON, REASON, REASON]);
  });

  /*
   * Two identifiers on one instrument share a record, so the record cannot tell their detail rows
   * apart. Each row is titled with its own DOI and carries the server's sentence as its help text,
   * which is what keeps the grouped alert as informative as one toast per identifier would be.
   */
  test("each detail row names its own identifier, so two PIDs on one instrument stay distinguishable", async () => {
    const tSpy = vi.spyOn(i18n, "t");
    mockBulkTransferReturning([
      pidinst({ outcome: "FAILED", reason: "B2INST said no" }, "10.82316/aaaa-aaaa"),
      pidinst({ outcome: "FAILED", reason: "DataCite said no" }, "10.82316/bbbb-bbbb"),
    ]);

    await transfer();

    const error = mockAddAlert.mock.calls.map(([alert]) => alert).find((alert) => alert.variant === "error");
    expect(error.details.map((d: { help: string }) => d.help)).toEqual(["B2INST said no", "DataCite said no"]);
    expect(
      tSpy.mock.calls
        .filter(([key]) => key === "inventory:identifierModel.alerts.externalUpdateFailed")
        .map(([, options]) => (options as { doi: string }).doi),
    ).toEqual(["10.82316/aaaa-aaaa", "10.82316/bbbb-bbbb"]);
  });

  /*
   * The frozen-record half of the grouping, which is the likelier one in practice: an accepted
   * B2INST record stays accepted, so a bulk transfer of a settled collection hits this and not the
   * failure path. Asserted separately because TypeScript catches a mistyped key but not the two
   * keys being swapped, and the count is read from the group rather than the whole set.
   */
  test("frozen records across a bulk transfer group into one notice, counted", async () => {
    const tSpy = vi.spyOn(i18n, "t");
    mockBulkTransferReturningMany(2, [
      pidinst({ outcome: "NOT_UPDATABLE", reason: "its community review has been accepted" }),
    ]);

    await transferMany(2);

    expect(mockAddAlert.mock.calls.map(([alert]) => alert.variant)).toEqual(["notice", "success", "notice"]);
    expect(
      tSpy.mock.calls.filter(([key]) => key === "inventory:identifierModel.alerts.externalUpdateNotPossibleMany"),
    ).toEqual([["inventory:identifierModel.alerts.externalUpdateNotPossibleMany", { count: 2 }]]);
  });

  /*
   * One provider can freeze while the other fails, so the two kinds have to stay separable: one
   * alert each, never merged into whichever variant happened to come first.
   */
  test("a mix of failures and frozen records raises one alert of each kind", async () => {
    mockBulkTransferReturningMany(2, [
      pidinst({ outcome: "FAILED", reason: "B2INST could not be reached" }),
      pidinst({ outcome: "NOT_UPDATABLE", reason: "already accepted" }, "10.82316/frozen"),
    ]);

    await transferMany(2);

    expect(mockAddAlert.mock.calls.map(([alert]) => alert.variant)).toEqual(["notice", "success", "error", "notice"]);
  });

  test("the departing owner's blanked identifier list is silence, not failure", async () => {
    mockBulkTransferReturning([]);

    await transfer();

    expect(mockAddAlert.mock.calls.map(([alert]) => alert.variant)).toEqual(["notice", "success"]);
  });
});
