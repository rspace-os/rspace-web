import ApiService, {
  BulkEndpointRecordSerialisation,
} from "../../common/InvApiService";
import {
  match,
  doNotAwait,
  omitNull,
  sameKeysAndValues,
  mapObject,
} from "../../util/Util";
import * as ArrayUtils from "../../util/ArrayUtils";
import {
  handleDetailedErrors,
  handleDetailedSuccesses,
  showToastWhilstPending,
} from "../../util/alerts";
import RsSet from "../../util/set";
import { type Editable } from "../definitions/Editable";
import {
  globalIdPatterns,
  type Id,
  type GlobalId,
  getSavedGlobalId,
} from "../definitions/BaseRecord";
import {
  type InventoryRecord,
  type ApiRecordType,
} from "../definitions/InventoryRecord";
import { type AdjustableTableRowLabel } from "../definitions/Tables";
import getRootStore from "../stores/RootStore";
import { mkAlert } from "../contexts/Alert";
import ContainerModel from "./ContainerModel";
import CacheFetcher from "./Fetcher/CacheFetcher";
import CoreFetcher from "./Fetcher/CoreFetcher";
import DynamicFetcher from "./Fetcher/DynamicFetcher";
import { type Username, type Person } from "../definitions/Person";
import Result from "./Result";
import { type Factory } from "../definitions/Factory";
import SampleModel from "./SampleModel";
import SubSampleModel, { type SubSampleAttrs } from "./SubSampleModel";
import { type TemplateAttrs } from "./TemplateModel";
import TreeModel, { type TreeAttrs } from "./TreeModel";
import { type TreeView } from "../definitions/TreeView";
import {
  action,
  computed,
  observable,
  makeObservable,
  runInAction,
} from "mobx";
import {
  type CoreFetcherArgs,
  type CoreFetcher as CoreFetcherInterface,
  type DynamicFetcher as DynamicFetcherInterface,
  type CacheFetcher as CacheFetcherInterface,
  type Search as SearchInterface,
  type AllowedTypeFilters,
  type UiConfig,
  type ResultType,
  type DeletedItems,
  type SearchView,
  type ExportOptions,
} from "../definitions/Search";
import { type Sample } from "../definitions/Sample";
import {
  getErrorMessage,
  InvalidState,
  UserCancelledAction,
} from "../../util/error";
import { type Basket } from "../definitions/Basket";
import { type SubSample } from "../definitions/SubSample";
import { noProgress } from "../../util/progress";
import {
  IsInvalid,
  IsValid,
  allAreValid,
} from "../../components/ValidatingSubmitButton";
import { type Quantity } from "./RecordWithQuantity";
import * as Parsers from "../../util/parsers";
import UtilResult from "../../util/result";

const DYNAMIC_VIEWS = ["TREE", "CARD"];
const CACHE_VIEWS = ["IMAGE", "GRID"];

export const getViewGroup = (
  view: SearchView
): "dynamic" | "cache" | "static" =>
  match<SearchView, "dynamic" | "cache" | "static">([
    [(v) => DYNAMIC_VIEWS.includes(v), "dynamic"],
    [(v) => CACHE_VIEWS.includes(v), "cache"],
    [() => true, "static"],
  ])(view);

const prepareRecordsForBulkApi = (
  records: Array<InventoryRecord>,
  opts?: { forceDelete?: boolean }
) =>
  records.map((record) => ({
    id: record.id,
    type: record.type,
    owner: { username: record.owner?.username },
    ...(opts ?? {}),
  }));

type SearchArgs = {
  fetcherParams?: CoreFetcherArgs;
  treeArgs?: Omit<TreeAttrs, "treeHolder"> & {
    treeHolder?: TreeAttrs["treeHolder"];
  };
  uiConfig?: Partial<UiConfig>;
  callbacks?: {
    /*
     * Note that this callback is called after setActiveResult (naturally) and
     * the resulting call to fetchAdditionalInfo have both completed.
     */
    setActiveResult?: (r: InventoryRecord | null) => void;
  };
  factory: Factory;
};

const DEFAULT_UI_CONFIG: UiConfig = {
  allowedSearchModules: new Set([
    "BENCHES",
    "TYPE",
    "STATUS",
    "OWNER",
    "SCAN",
    "TAG",
    "SAVEDSEARCHES",
    "SAVEDBASKETS",
  ]),
  allowedTypeFilters: new Set([
    "ALL",
    "CONTAINER",
    "SAMPLE",
    "SUBSAMPLE",
    "TEMPLATE",
  ]),
  mainColumn: "Name",
  // note: there is a non-breaking space (U+00A0) between "Global" and "ID"
  adjustableColumns: ["Global ID", "Owner", "Last Modified"],
  selectionMode: "MULTIPLE",
  highlightActiveResult: true,
  hideContentsOfChip: false,
  selectionLimit: Infinity,
  onlyAllowSelectingEmptyLocations: false,
};

export default class Search implements SearchInterface {
  activeResult: InventoryRecord | null = null;
  processingContextActions: boolean = false;
  error: string | undefined;
  tree: TreeView;
  searchView: SearchView = "LIST";
  dynamicFetcher: DynamicFetcherInterface;
  staticFetcher: CoreFetcherInterface;
  cacheFetcher: CacheFetcherInterface;
  alwaysFilterOut: (r: InventoryRecord) => boolean = () => false;
  callbacks:
    | {
        setActiveResult?: (r: InventoryRecord | null) => void;
      }
    | undefined;

  /*
   *  When set, it overrides the default behaviour of reperforming a search
   *  when the user changes the type filters allowing the code to do custom
   *  behaviour such as updating the URL.
   */
  overrideSearchOnFilter: ((args: CoreFetcherArgs) => void) | null;

  /*
   * For simultaneously editing a bunch of records.
   */
  batchEditingRecords: RsSet<InventoryRecord> | null;
  editLoading: "no" | "batch" | "single";

  canEditActiveResult: boolean;
  uiConfig: UiConfig;
  factory: Factory;

  constructor({
    fetcherParams,
    treeArgs,
    uiConfig,
    callbacks,
    factory,
  }: SearchArgs) {
    makeObservable(this, {
      activeResult: observable,
      processingContextActions: observable,
      error: observable,
      tree: observable,
      searchView: observable,
      dynamicFetcher: observable,
      staticFetcher: observable,
      cacheFetcher: observable,
      alwaysFilterOut: observable,
      canEditActiveResult: observable,
      uiConfig: observable,
      overrideSearchOnFilter: observable,
      batchEditingRecords: observable,
      editLoading: observable,
      setActiveResult: action,
      deleteRecords: action,
      updateStateAfterDelete: action,
      restoreRecords: action,
      updateStateAfterRestore: action,
      duplicateRecords: action,
      exportRecords: action,
      splitRecord: action,
      replaceResult: action,
      transferRecords: action,
      updateStateAfterTransfer: action,
      setSearchView: action,
      refetchActiveResult: action,
      setAdjustableColumn: action,
      setOwner: action,
      setBench: action,
      setTypeFilter: action,
      setupAndPerformInitialSearch: action,
      setProcessingContextActions: action,
      enableBatchEditing: action,
      disableBatchEditing: action,
      setEditLoading: action,
      results: computed,
      filteredResults: computed,
      count: computed,
      fetcher: computed,
      selectedResults: computed,
      allSelectedAvailable: computed,
      allSelectedDeleted: computed,
      mixedSelectedStatus: computed,
      globalIdsOfSearchResults: computed,
      enableAdvancedOptions: computed,
      showBenchFilter: computed,
      showTypeFilter: computed,
      showStatusFilter: computed,
      showOwnershipFilter: computed,
      showBarcodeScan: computed,
      showTagsFilter: computed,
      showSavedSearches: computed,
      showSavedBaskets: computed,
      adjustableColumnOptions: computed,
      benchSearch: computed,
      allowedStatusFilters: computed,
      allowedTypeFilters: computed,
      loading: computed,
      loadingBatchEditing: computed,
      batchEditingRecordsByType: computed,
    });
    this.tree = new TreeModel({
      treeHolder: this,
      filteredTypes: ["container", "subSample", "sample", "sampleTemplate"],
      ...treeArgs,
    });
    this.dynamicFetcher = new DynamicFetcher(factory, fetcherParams ?? null);
    this.staticFetcher = new CoreFetcher(factory, fetcherParams);
    this.cacheFetcher = new CacheFetcher(factory, fetcherParams ?? null);

    this.canEditActiveResult = false;
    this.uiConfig = {
      ...DEFAULT_UI_CONFIG,
      ...uiConfig,
    };
    this.overrideSearchOnFilter = null;

    this.batchEditingRecords = null;
    this.editLoading = "no";

    this.callbacks = callbacks;
    this.factory = factory;
  }

  setProcessingContextActions(value: boolean): void {
    this.processingContextActions = value;
  }

  setEditLoading(value: "no" | "batch" | "single"): void {
    this.editLoading = value;
  }

  get results(): Array<InventoryRecord> {
    return this.fetcher.results ?? [];
  }

  get filteredResults(): Array<InventoryRecord> {
    /*
     * Filtering results locally, e.g. removing the results that the user does not own from the
     *  total search results resulted in breaking pagination. This stub has been left so as to
     *  make possible reattempting this, without having to reimplement the rest of the logic
     *  that works fine.
     */
    return this.results;
  }

  get count(): number {
    return this.fetcher.count;
  }

  get fetcher(): CoreFetcherInterface {
    return match<SearchView, CoreFetcherInterface>([
      [(view) => DYNAMIC_VIEWS.includes(view), this.dynamicFetcher],
      [(view) => CACHE_VIEWS.includes(view), this.cacheFetcher],
      [() => true, this.staticFetcher],
    ])(this.searchView);
  }

  get selectedResults(): Array<InventoryRecord> {
    return this.filteredResults.filter((r) => r.selected);
  }

  get allSelectedAvailable(): boolean {
    return this.selectedResults.every((r) => !r.deleted);
  }

  get allSelectedDeleted(): boolean {
    return this.selectedResults.every((r) => r.deleted);
  }

  get mixedSelectedStatus(): boolean {
    return (
      this.selectedResults.some((r) => r.deleted) &&
      this.selectedResults.some((r) => !r.deleted)
    );
  }

  async cleanUpActiveResult(
    options: { releaseLock?: boolean } = {}
  ): Promise<void> {
    const { releaseLock = true } = options;
    const activeResult = this.activeResult;
    if (activeResult) {
      activeResult.clearAllScopedToasts();
      if (releaseLock) await activeResult.setEditing(false, false);
    }
  }

  async cleanBatchEditing() {
    await Promise.all<Array<Promise<void>>>(
      this.batchEditingRecords?.toArray().map(async (r) => {
        await r.setEditing(false, true, true);
      }) ?? []
    );
    this.disableBatchEditing();
  }

  async enableBatchEditing(records: RsSet<InventoryRecord>): Promise<void> {
    await this.setActiveResult(null, { defaultToFirstResult: false });
    runInAction(() => {
      this.batchEditingRecords = records;
    });
    for (const r of records) {
      r.setFieldsStateForBatchEditing();
    }
  }

  disableBatchEditing(): void {
    this.batchEditingRecords = null;
  }

  get loadingBatchEditing(): boolean {
    return this.editLoading === "batch" && !this.batchEditingRecords;
  }

  get batchEditingRecordsByType():
    | null
    | { type: "container"; records: RsSet<ContainerModel> }
    | { type: "sample"; records: RsSet<SampleModel> }
    | { type: "subSample"; records: RsSet<SubSampleModel> }
    | { type: "mixed"; records: RsSet<Result> } {
    if (!this.batchEditingRecords) return null;
    const records: RsSet<InventoryRecord> = this.batchEditingRecords;
    if (records.every((r) => r instanceof ContainerModel)) {
      return {
        type: "container",
        records: records.filterClass(ContainerModel),
      };
    }
    if (records.every((r) => r instanceof SampleModel)) {
      return { type: "sample", records: records.filterClass(SampleModel) };
    }
    if (records.every((r) => r instanceof SubSampleModel)) {
      return {
        type: "subSample",
        records: records.filterClass(SubSampleModel),
      };
    }
    return {
      type: "mixed",
      records: records.filterClass(Result),
    };
  }

  async setActiveResult(
    result: InventoryRecord | null = null,
    options: { defaultToFirstResult?: boolean; force?: boolean } = {}
  ): Promise<void> {
    const { defaultToFirstResult = true, force = false } = options;
    if (this.canEditActiveResult && !force) {
      if (!(await getRootStore().uiStore.confirmDiscardAnyChanges()))
        throw new UserCancelledAction("Unsaved changes.");
    }

    await this.cleanBatchEditing();
    await this.cleanUpActiveResult({ releaseLock: !force });

    const assignment = action(async (r: Result | null) => {
      if (r) {
        if (!r.infoLoaded && r.id) {
          r.setLoading(true); // don't give react a chance to render without loading being true
        }
        this.activeResult = r;
        if (!r.infoLoaded && r.id) await r.fetchAdditionalInfo();
        this.callbacks?.setActiveResult?.(r);
      } else if (this.filteredResults.length > 0 && defaultToFirstResult) {
        if (this.filteredResults[0] instanceof Result)
          await assignment(this.filteredResults[0]);
      } else {
        this.activeResult = null;
        this.callbacks?.setActiveResult?.(null);
      }
    });
    if (result instanceof Result || result === null) await assignment(result);
  }

  /*
   * Fetches the bench owned by given user, as specified by their username, and
   * navigates to their bench. Uses the current user if unspecified.
   */
  async toBench(username?: Username) {
    const { peopleStore } = getRootStore();

    const benchOwner: Person | null = username
      ? (await peopleStore.getUser(username)) ?? null
      : peopleStore.currentUser;

    this.setOwner(null, false);
    this.setTypeFilter("ALL", false);
    this.setBench(benchOwner);
  }

  /*
   * Deletes the passed records, displays toasts accordingly, and invokes the
   * refreshing of the UI's state.
   */
  async deleteRecords(
    records: Array<InventoryRecord>,
    opts?: { forceDelete?: boolean }
  ): Promise<void> {
    this.setProcessingContextActions(true);
    const { uiStore } = getRootStore();

    type SubSampleResponse = {
      storedInContainer: boolean;
      parentContainers: Array<{
        name: string;
        globalId: GlobalId;
      }>;
    } & Omit<SubSampleAttrs, "sample">;
    type SampleResponse = {
      globalId: GlobalId;
      type: string;
      canBeDeleted?: boolean;
      subSamples: Array<SubSampleResponse>;
    };

    try {
      const { data } = await showToastWhilstPending(
        "Sending to trash...",
        ApiService.bulk<{
          results: Array<{
            error: { errors: Array<string> };
            record: null | SampleResponse;
          }>;
          errorCount: number;
        }>(prepareRecordsForBulkApi(records, opts), "DELETE", false)
      );

      /*
       * We treat samples different to other record types because when a sample
       * is deleted, each of its subsamples must be deleted too. If the
       * subsamples are currently inside containers then the user is presented
       * with an error detailing these subsamples and where to find them.
       */
      const samplesThatCouldNotBeDeleted = ArrayUtils.filterNull(
        data.results.map(({ record }) => record)
      ).filter(({ type, canBeDeleted }) => type === "SAMPLE" && !canBeDeleted);
      const samplesThatCouldBeDeleted = ArrayUtils.filterNull(
        data.results.map(({ record }) => record)
      ).filter((r) => r.type === "SAMPLE" && r.canBeDeleted);

      const factory = this.factory.newFactory();
      const successfullyDeleted = [
        ...ArrayUtils.filterNull(
          data.results.filter(({ error }) => !error).map(({ record }) => record)
        )
          .filter((record) => record.type !== "SAMPLE")
          /*
           * The list is reversed because the server processes each record in
           * turn and the processing of one record will at times impact how
           * subsequent ones are processed and we always want the last piece of
           * this cascaded state. For example, when deleting both of the
           * subsamples of a sample, the first record to be returned will have
           * a sample whose subSampleCount will be 1 and the second will have a
           * sample whose subSampleCount will be 0. We want the second sample
           * to be initialised by the factory and to be the sample used for
           * both subsamples so we reverse the list so that the factory sees
           * the second one first.
           */
          .toReversed(),
        ...samplesThatCouldBeDeleted,
      ].map((record) => {
        const newRecord = factory.newRecord(record);
        newRecord.populateFromJson(factory, record, null);
        return newRecord;
      });

      if (samplesThatCouldNotBeDeleted.length > 0) {
        const subsamplesThatPreventedSampleDeletion =
          samplesThatCouldNotBeDeleted
            .flatMap((s) =>
              s.subSamples.map<[SampleResponse, SubSampleResponse]>((ss) => [
                s,
                ss,
              ])
            )
            .filter(([, ss]) => ss.storedInContainer);
        uiStore.addAlert(
          mkAlert({
            variant: "error",
            title:
              "Some of the samples could not be trashed because the subsamples are in containers.",
            message: "Please move them to the trash first.",
            details: subsamplesThatPreventedSampleDeletion.map(([s, ss]) => ({
              title: `Could not trash "${
                ss.name ?? "UNKNOWN"
              }" ${ArrayUtils.head(ss.parentContainers)
                .map(({ name, globalId }) => `(in ${name} ${globalId ?? ""})`)
                .orElse("")}`,
              variant: "error",
              record: factory.newRecord({
                ...ss,
                sample: s,
              } as any as Record<string, unknown> & { globalId: GlobalId }),
            })),
            actionLabel: "Move all to trash",
            onActionClick: () => {
              void this.deleteRecords(records, { forceDelete: true });
            },
          })
        );
      }
      handleDetailedErrors(
        data.errorCount,
        ArrayUtils.zipWith(data.results, records, (d, r) => ({
          response: d,
          record: r,
        })),
        "sending to trash",
        (erroredRecords) => this.deleteRecords(erroredRecords)
      );
      if (successfullyDeleted.length > 0)
        handleDetailedSuccesses(successfullyDeleted, "trashed");
      this.offerToDeleteNowEmptySamples(successfullyDeleted);

      await this.updateStateAfterDelete(new RsSet(successfullyDeleted));
    } catch (error) {
      uiStore.addAlert(
        mkAlert({
          title: "Sending to trash failed.",
          message: getErrorMessage(error, "Unknown reason"),
          variant: "error",
        })
      );
      console.error("Could not perform deletion.", error);
    } finally {
      this.setProcessingContextActions(false);
    }
  }

  /*
   * After a delete action has occured, there are various parts of the UI that
   * need to refetch their associated data to ensure they are displaying the
   * correct state of the system.
   */
  async updateStateAfterDelete(deletedRecords: RsSet<InventoryRecord>) {
    const {
      searchStore,
      peopleStore: { currentUser },
      uiStore,
    } = getRootStore();
    const deletedGlobalIds: RsSet<GlobalId> =
      deletedRecords.map(getSavedGlobalId);

    // update sidebar bench count
    if (currentUser) void currentUser.getBench();

    await this.fetcher.reperformCurrentSearch();

    // if the activeResult has been deleted then set to first result
    if (
      searchStore.activeResult?.globalId &&
      deletedGlobalIds.has(searchStore.activeResult.globalId)
    ) {
      /*
       * force setting active result because if the active result has unsaved
       * changes we want to unconditionally discard them
       */
      await searchStore.search.setActiveResult(null, { force: true });
      uiStore.unsetDirty();
      uiStore.setVisiblePanel("left");
    } else {
      // e.g. sample's quantity may have changed
      this.refetchActiveResult(deletedGlobalIds);
    }

    if (searchStore.activeResult instanceof ContainerModel) {
      const cont = searchStore.activeResult;
      if (cont.state === "preview") await cont.fetchAdditionalInfo();
      cont.refreshAssociatedSearch();
    }
  }

  offerToDeleteNowEmptySamples(deletedRecords: Array<InventoryRecord>) {
    const { uiStore, searchStore } = getRootStore();
    const justSubsamplesThatAreBeingDeleted: Array<SubSampleModel> =
      ArrayUtils.filterClass(SubSampleModel, deletedRecords);
    const samplesOfDeletedSubSamples: Array<SampleModel> =
      justSubsamplesThatAreBeingDeleted.map((r) => r.sample);
    /*
     * Creating a set works because each sample is only instantiated once due
     * to MemoisedFactory
     */
    const nowEmptySamples = new Set(
      samplesOfDeletedSubSamples.filter((s) => s.subSamplesCount === 0)
    );

    for (const sample of nowEmptySamples) {
      uiStore.addAlert(
        mkAlert({
          message: `Send Sample ${sample.name} to trash too?`,
          variant: "notice",
          isInfinite: true,
          actionLabel: "yes",
          onActionClick: doNotAwait(async () => {
            await this.deleteRecords([sample]);
            await sample.fetchAdditionalInfo();
            await searchStore.search.fetcher.performInitialSearch(null);
          }),
        })
      );
    }
  }

  /*
   * Restored the passed records, displays toasts accordingly, and invokes the
   * refreshing of the UI's state.
   */
  async restoreRecords(records: Array<InventoryRecord>): Promise<void> {
    this.setProcessingContextActions(true);
    const { uiStore } = getRootStore();

    try {
      const { data } = await showToastWhilstPending(
        "Restoring...",
        ApiService.bulk<{
          results: Array<{
            error: { errors: Array<string> };
            record: Record<string, unknown> & { globalId: GlobalId };
          }>;
          errorCount: number;
        }>(prepareRecordsForBulkApi(records), "RESTORE", false)
      );

      const factory = this.factory.newFactory();
      const successfullyRestored = data.results
        .filter(({ error }) => !error)
        .map(({ record }) => {
          const newRecord = factory.newRecord(record);
          newRecord.populateFromJson(factory, record, {});
          return newRecord;
        });

      handleDetailedErrors(
        data.errorCount,
        ArrayUtils.zipWith(data.results, records, (d, r) => ({
          response: d,
          record: r,
        })),
        "restore",
        (erroredRecords) => this.restoreRecords(erroredRecords)
      );
      handleDetailedSuccesses(successfullyRestored, "restored");
      await this.updateStateAfterRestore(new RsSet(successfullyRestored));
    } catch (error) {
      uiStore.addAlert(
        mkAlert({
          title: "Restore failed.",
          message: getErrorMessage(error, "Unknown reason"),
          variant: "error",
        })
      );
      console.error("Could not perform restoration.", error);
    } finally {
      this.setProcessingContextActions(false);
    }
  }

  /*
   * After a restore action has occured, there are various parts of the UI that
   * need to refetch their associated data to ensure they are displaying the
   * correct state of the system.
   */
  async updateStateAfterRestore(restoredRecords: RsSet<InventoryRecord>) {
    const {
      peopleStore: { currentUser },
      searchStore: { activeResult },
    } = getRootStore();
    const recordIds = restoredRecords.map((r) => r.globalId);

    // update sidebar bench count
    if (currentUser) void currentUser.getBench();

    await this.fetcher.performInitialSearch(null);

    if (activeResult) {
      if (recordIds.has(activeResult.globalId))
        await activeResult.fetchAdditionalInfo();

      // Re-search because restoring a sample also restores its subsamples
      if (activeResult instanceof SampleModel)
        activeResult.refreshAssociatedSearch();

      if (this.isActiveResultTemplateOfAny(restoredRecords))
        activeResult.refreshAssociatedSearch();
    }
  }

  async duplicateRecords(records: Array<InventoryRecord>): Promise<void> {
    this.setProcessingContextActions(true);
    const { peopleStore, uiStore } = getRootStore();
    try {
      const { data } = await showToastWhilstPending(
        "Duplicating...",
        ApiService.bulk<{
          results: Array<{
            error: { errors: Array<string> };
            record: Record<string, unknown> & { globalId: GlobalId };
          }>;
          errorCount: number;
        }>(prepareRecordsForBulkApi(records), "DUPLICATE", true)
      );
      if (
        handleDetailedErrors(
          data.errorCount,
          ArrayUtils.zipWith(data.results, records, (d, r) => ({
            response: d,
            record: r,
          })),
          "duplicate",
          (erroredRecords) => this.duplicateRecords(erroredRecords)
        )
      ) {
        await this.fetcher.performInitialSearch(null);
        return;
      }

      // e.g. Sample's quantity may have changed
      const recordIds: Set<GlobalId> = new Set(records.map(getSavedGlobalId));
      this.refetchActiveResult(recordIds);

      const factory = this.factory.newFactory();
      const newRecords = data.results.map((r) => {
        const newRecord = factory.newRecord(r.record);
        newRecord.populateFromJson(factory, r.record, null);
        return newRecord;
      });

      const newBenchItems = [
        ...(newRecords.some(
          (r) => r.hasSubSamples || r instanceof SubSampleModel
        )
          ? ["subsamples"]
          : []),
        ...(newRecords.some((r) => r instanceof ContainerModel)
          ? ["containers"]
          : []),
      ];

      handleDetailedSuccesses(
        newRecords,
        "duplicated",
        () => "created",
        newBenchItems.length > 0
          ? `Newly created ${newBenchItems.join(
              " and "
            )} are placed on your Bench.`
          : null
      );
      if (peopleStore.currentUser) void peopleStore.currentUser.getBench();
      await this.fetcher.performInitialSearch(null);
      if (this.activeResult && data.results.length > 0) {
        await this.setActiveResult(newRecords[0]);
      }
    } catch (error) {
      uiStore.addAlert(
        mkAlert({
          title: "Duplication failed.",
          message: getErrorMessage(error, "Unknown reason"),
          variant: "error",
        })
      );
      console.error("Could not perform duplication.", error);
    } finally {
      this.setProcessingContextActions(false);
    }
  }

  async splitRecord(copies: number, subsample: SubSample): Promise<void> {
    if (!(subsample instanceof SubSampleModel))
      throw new Error("Can only split SubSamples");
    this.setProcessingContextActions(true);
    if (!subsample.id) throw new Error("id is required.");
    const id = subsample.id;

    const { peopleStore, uiStore } = getRootStore();
    try {
      const { data } = await showToastWhilstPending(
        "Splitting...",
        ApiService.post<Array<SubSampleAttrs>>(
          `subSamples/${id}/actions/split`,
          {
            split: true,
            numSubSamples: `${copies}`,
          }
        )
      );

      if (peopleStore.currentUser) void peopleStore.currentUser.getBench();
      void this.fetcher.performInitialSearch(null);

      const factory = this.factory.newFactory();
      handleDetailedSuccesses(
        [
          subsample,
          ...data.map((r) => {
            const newRecord = factory.newRecord(r);
            newRecord.populateFromJson(factory, r, null);
            return newRecord;
          }),
        ],
        "split",
        (r) => (r === subsample ? "Updated" : "Created"),
        "Newly created subsamples are placed on your Bench."
      );
      await this.updateStateAfterSplit(subsample);
    } catch (error) {
      console.error(error);
      uiStore.addAlert(
        mkAlert({
          title: "Splitting subsample failed.",
          message: getErrorMessage(error, "Unknown reason"),
          variant: "error",
          duration: 8000,
        })
      );
    } finally {
      this.setProcessingContextActions(false);
    }
  }

  async updateStateAfterSplit(subsample: InventoryRecord) {
    const {
      searchStore: { activeResult },
    } = getRootStore();
    const promises = [];

    promises.push(activeResult?.refreshAssociatedSearch() ?? Promise.resolve());

    /*
     * If the activeResult is the parent sample of the subsample being split,
     * then that means we're doing the splitting from the context menu inside
     * the sample form's subsample listing. As such, we should refresh the
     * activeResult so that the form fields that display the number of
     * subsamples remains correct.
     *
     * If the activeResult is itself the subsample being split then we should
     * refresh it so that the label in the quantity field that displays the
     * number of siblings is similarly updated.
     */
    if (
      subsample instanceof SubSampleModel &&
      (subsample.sample.globalId === activeResult?.globalId ||
        subsample.globalId === activeResult?.globalId)
    ) {
      promises.push(activeResult?.fetchAdditionalInfo() ?? Promise.resolve());
    }

    await Promise.all(promises);
  }

  replaceResult(result: Result) {
    this.fetcher.replaceResult(result);
  }

  async transferRecords(
    username: Username,
    records: Array<InventoryRecord>
  ): Promise<void> {
    const { peopleStore } = getRootStore();
    this.setProcessingContextActions(true);

    const newOwner = await peopleStore.getUser(username);
    records.forEach((r) => {
      if (newOwner) r.owner = newOwner;
    });

    try {
      const { data } = await showToastWhilstPending(
        "Transferring...",
        ApiService.bulk<{
          results: Array<{
            error: { errors: Array<string> };
            record: Record<string, unknown> & { globalId: GlobalId };
          }>;
          errorCount: number;
        }>(prepareRecordsForBulkApi(records), "CHANGE_OWNER", true)
      );

      const factory = this.factory.newFactory();
      const successfullyTranferred = data.results
        .filter(({ error }) => !error)
        .map(({ record }) => {
          const newRecord = factory.newRecord(record);
          newRecord.populateFromJson(factory, record, null);
          return newRecord;
        });
      const recordsOnBench = successfullyTranferred.every((r) =>
        r.isOnWorkbench()
      );
      const helpMessage = recordsOnBench
        ? `The records have been moved to ${username}'s bench`
        : null;

      handleDetailedErrors(
        data.errorCount,
        ArrayUtils.zipWith(data.results, records, (d, r) => ({
          response: d,
          record: r,
        })),
        "transfer",
        (erroredRecords) => this.transferRecords(username, erroredRecords)
      );
      handleDetailedSuccesses(
        successfullyTranferred,
        "transferred",
        () => "transferred",
        helpMessage
      );
      await this.updateStateAfterTransfer(new RsSet(successfullyTranferred));
    } catch (error) {
      getRootStore().uiStore.addAlert(
        mkAlert({
          title: "Transfer failed.",
          message: getErrorMessage(error, "Unknown reason."),
          variant: "error",
        })
      );
      console.error("Could not perform transfer.", error);
    } finally {
      this.setProcessingContextActions(false);
    }
  }

  /*
   * After a transfer action has occured, there are various parts of the UI
   * that need to refetch their associated data to ensure they are displaying
   * the correct state of the system.
   */
  async updateStateAfterTransfer(
    transferredRecords: RsSet<InventoryRecord>
  ): Promise<void> {
    const {
      peopleStore: { currentUser },
      searchStore,
      uiStore,
    } = getRootStore();
    const transferredGlobalIds = transferredRecords.map((r) => r.globalId);

    // update sidebar bench count
    if (currentUser) void currentUser.getBench();

    await this.fetcher.performInitialSearch(null);

    if (searchStore.activeResult) {
      const activeResult = searchStore.activeResult;
      // if the activeResult has been transferred then set to first result
      if (transferredGlobalIds.has(activeResult.globalId)) {
        try {
          await searchStore.search.setActiveResult();
          uiStore.setVisiblePanel("left");
        } catch (e) {
          if (e instanceof UserCancelledAction) return;
          throw e;
        }
      }

      /*
       * if the activeResult is a template of one of the transferred samples
       * then refresh the template's sample listing
       */
      if (this.isActiveResultTemplateOfAny(transferredRecords))
        activeResult.refreshAssociatedSearch();
    }
  }

  async createTemplateFromSample(
    name: string,
    sample: Sample,
    includeContentForFields: Set<Id>
  ): Promise<void> {
    const { uiStore } = getRootStore();
    try {
      if (!sample.infoLoaded) await sample.fetchAdditionalInfo();
      const args = {
        ...(await sample.sampleCreationParams(includeContentForFields)),
        name,
      };
      const { data } = await ApiService.post<TemplateAttrs>(
        "sampleTemplates",
        args
      );
      const factory = this.factory.newFactory();
      const template = factory.newRecord(data);
      uiStore.addAlert(
        mkAlert({
          message: `Template created successfully.`,
          variant: "success",
          details: [
            {
              title: template.name,
              variant: "success",
              record: template,
            },
          ],
        })
      );
      void this.fetcher.performInitialSearch(null);
    } catch (error) {
      uiStore.addAlert(
        mkAlert({
          title: `Template creation failed.`,
          message: getErrorMessage(error, "Unknown reason."),
          variant: "error",
          details: Parsers.objectPath(["response", "data", "errors"], error)
            .flatMap(Parsers.isArray)
            .flatMap((errors) =>
              UtilResult.all(...errors.map(Parsers.isString)).map((titles) =>
                titles.map((title) => ({
                  title,
                  variant: "error" as const,
                }))
              )
            )
            .orElse(undefined),
        })
      );
      console.error("Could not create template from sample.", error);
      throw error;
    }
  }

  async createNewSubsamples(opts: {
    sample: Sample;
    numberOfNewSubsamples: number;
    quantityPerSubsample: Quantity;
  }): Promise<void> {
    const { uiStore, searchStore } = getRootStore();
    try {
      const { data } = await ApiService.post<ReadonlyArray<SubSampleAttrs>>(
        "subSamples",
        {
          sampleId: opts.sample.id,
          numSubSamples: `${opts.numberOfNewSubsamples}`,
          singleSubSampleQuantity: opts.quantityPerSubsample,
        }
      );
      await Promise.all([
        opts.sample.fetchAdditionalInfo(),
        opts.sample.refreshAssociatedSearch(),
        searchStore.search.fetcher.parentGlobalId === opts.sample.globalId
          ? searchStore.search.fetcher.performInitialSearch(null)
          : Promise.resolve(),
      ]);

      const factory = this.factory.newFactory();
      const successfullyCreated = data.map((record) => {
        const newRecord = factory.newRecord(record);
        newRecord.populateFromJson(factory, record, null);
        return newRecord;
      });

      uiStore.addAlert(
        mkAlert({
          message: "Successfully created new subsamples.",
          variant: "success",
          details: successfullyCreated.map((r) => ({
            title: r.name,
            variant: "success",
            record: r,
          })),
        })
      );
    } catch (error) {
      uiStore.addAlert(
        mkAlert({
          title: "Subsample creation failed.",
          message: getErrorMessage(error, "Unknown reason."),
          variant: "error",
          details: Parsers.objectPath(["response", "data", "errors"], error)
            .flatMap(Parsers.isArray)
            .flatMap((errors) =>
              UtilResult.all(...errors.map(Parsers.isString)).map((titles) =>
                titles.map((title) => ({
                  title,
                  variant: "error" as const,
                }))
              )
            )
            .orElse(undefined),
        })
      );
      throw error;
    }
  }

  async exportRecords(
    exportOptions: ExportOptions,
    records: Array<InventoryRecord>
  ): Promise<void> {
    this.setProcessingContextActions(true);
    const { uiStore, trackingStore } = getRootStore();
    try {
      const {
        exportMode,
        includeSubsamplesInSample,
        includeContainerContent,
        resultFileType,
      } = exportOptions;
      const globalIds = records.map((r) => r.globalId);
      const params = new FormData();

      params.append(
        "exportSettings",
        JSON.stringify({
          globalIds,
          exportMode,
          // if omitted, ZIP is assumed
          ...(resultFileType === null ? {} : { resultFileType }),
          // leaving values as string in RadioField, converting to boolean here
          ...(includeSubsamplesInSample === null
            ? {}
            : {
                includeSubsamplesInSample:
                  includeSubsamplesInSample === "INCLUDE",
              }),
          ...(includeContainerContent === null
            ? {}
            : {
                includeContainerContent: includeContainerContent === "INCLUDE",
              }),
        })
      );
      const { data } = await showToastWhilstPending(
        "Exporting...",
        ApiService.post<{ _links: Array<{ link: string; rel: string }> }>(
          "export",
          params
        )
      );
      const downloadLink = data._links[1];
      const fileName = downloadLink.link.split("downloadArchive/")[1];
      // create link for download
      const link = document.createElement("a");
      link.setAttribute("href", downloadLink.link);
      link.setAttribute("rel", downloadLink.rel);
      link.setAttribute("download", fileName);
      link.click(); // trigger download
      trackingStore.trackEvent("user:export:selection:Inventory", {
        ...exportOptions,
        count: {
          ...mapObject(
            (_type: ApiRecordType, list) => list.length,
            ArrayUtils.groupBy(({ type }) => type, records)
          ),
          total: records.length,
        },
      });
    } catch (error) {
      uiStore.addAlert(
        mkAlert({
          title: `Data export failed.`,
          message: getErrorMessage(error, "Unknown reason."),
          variant: "error",
        })
      );
      console.error(`Could not export the selected records.`, error);
      throw error;
    } finally {
      this.setProcessingContextActions(false);
    }
  }

  async setSearchView(view: SearchView = "LIST"): Promise<void> {
    const selectedResultsIds = this.selectedResults.map((res) => res.globalId);
    const currentSearchParams = this.fetcher.generateParams({});
    const priorView = this.searchView;
    const priorFetcher = this.fetcher;

    runInAction(() => {
      this.searchView = view;
    });

    this.staticFetcher.setAttributes(currentSearchParams);
    this.dynamicFetcher.setAttributes(currentSearchParams);
    this.cacheFetcher.setAttributes(currentSearchParams);

    if (getViewGroup(priorView) !== getViewGroup(view)) {
      await this.fetcher.performInitialSearch(currentSearchParams);

      this.filteredResults.map((res) =>
        res.toggleSelected(selectedResultsIds.includes(res.globalId))
      );

      /*
       * If the new view doesn't have a record with the same Global ID as a
       * given selected record in the prior fetcher's results, then it should
       * be deselected. This is because otherwise other aspects of the UI, such
       * as the context menu at the top of the viewport being disabled, will
       * continue to alter their behaviour without apparent reason.
       */
      const gIdsOfResultsOfNewFetcher = new Set(
        this.filteredResults.map((r) => r.globalId)
      );
      priorFetcher.results.map((res) =>
        res.toggleSelected(
          res.selected && gIdsOfResultsOfNewFetcher.has(res.globalId)
        )
      );
    }
  }

  get globalIdsOfSearchResults(): Set<GlobalId> {
    return new Set(this.filteredResults.map(getSavedGlobalId));
  }

  isInResults(record: InventoryRecord): boolean {
    if (!record.globalId) return false;
    return this.globalIdsOfSearchResults.has(record.globalId);
  }

  refetchActiveResult(recordIds: Set<GlobalId>): void {
    const activeResult = getRootStore().searchStore.activeResult;
    if (activeResult) activeResult.updateBecauseRecordsChanged(recordIds);
  }

  get enableAdvancedOptions(): boolean {
    return !this.fetcher.permalink;
  }

  get showBenchFilter(): boolean {
    return (
      this.enableAdvancedOptions &&
      Boolean(this.uiConfig.allowedSearchModules.has("BENCHES"))
    );
  }

  get showTypeFilter(): boolean {
    return (
      this.enableAdvancedOptions &&
      Boolean(this.uiConfig.allowedSearchModules.has("TYPE"))
    );
  }

  get showStatusFilter(): boolean {
    return (
      this.enableAdvancedOptions &&
      Boolean(this.uiConfig.allowedSearchModules.has("STATUS"))
    );
  }

  get showOwnershipFilter(): boolean {
    return (
      this.enableAdvancedOptions &&
      Boolean(this.uiConfig.allowedSearchModules.has("OWNER"))
    );
  }

  get showBarcodeScan(): boolean {
    return Boolean(this.uiConfig.allowedSearchModules.has("SCAN"));
  }

  get showTagsFilter(): boolean {
    return Boolean(this.uiConfig.allowedSearchModules.has("TAG"));
  }

  get showSavedSearches(): boolean {
    return Boolean(
      this.uiConfig.allowedSearchModules.has("SAVEDSEARCHES") &&
        getRootStore().searchStore.savedSearches.length > 0
    );
  }

  get showSavedBaskets(): boolean {
    return Boolean(this.uiConfig.allowedSearchModules.has("SAVEDBASKETS"));
  }

  get adjustableColumnOptions(): RsSet<AdjustableTableRowLabel> {
    return new RsSet<AdjustableTableRowLabel>().union(
      ...this.filteredResults.map(
        (r) => new RsSet([...r.adjustableTableOptions().keys()])
      )
    );
  }

  setAdjustableColumn(value: AdjustableTableRowLabel, index: number) {
    this.uiConfig.adjustableColumns[index] = value;
  }

  performSearch() {
    this.fetcher.pageNumber = 0; // setPage performs network activity
    const searchParams = this.fetcher.generateParams({});
    if (this.overrideSearchOnFilter) {
      this.overrideSearchOnFilter(searchParams);
    } else {
      void this.fetcher.performInitialSearch(searchParams);
    }
  }

  setPageSize(pageSize: number) {
    // Only staticFetcher is assigned because only list view has a configurable page size
    this.staticFetcher.setPageSize(pageSize);
    this.performSearch();
  }

  async setPage(pageNumber: number) {
    await this.fetcher.setPage(pageNumber);
  }

  setOwner(user: Person | null, doSearch: boolean = true) {
    this.staticFetcher.setOwner(user);
    this.dynamicFetcher.setOwner(user);
    this.cacheFetcher.setOwner(user);
    if (doSearch) this.performSearch();
  }

  setBench(user: Person | null, doSearch: boolean = true) {
    this.staticFetcher.setBenchOwner(user);
    this.dynamicFetcher.setBenchOwner(user);
    this.cacheFetcher.setBenchOwner(user);
    if (doSearch) this.performSearch();

    // in the move dialog, choosing a bench should set it as the target
    if (user) {
      doNotAwait(async () => {
        const bench = user.bench ?? (await user.getBench());
        await getRootStore().moveStore.setTargetContainer(bench);
      })();
    }
  }

  setTypeFilter(resultType: ResultType, doSearch: boolean = true) {
    this.staticFetcher.setResultType(resultType);
    this.dynamicFetcher.setResultType(resultType);
    this.cacheFetcher.setResultType(resultType);
    if (doSearch) this.performSearch();
  }

  setDeletedItems(deletedItems: DeletedItems, doSearch: boolean = true) {
    this.staticFetcher.setDeletedItems(deletedItems);
    this.dynamicFetcher.setDeletedItems(deletedItems);
    this.cacheFetcher.setDeletedItems(deletedItems);
    if (doSearch) this.performSearch();
  }

  setParentGlobalId(parentGlobalId: GlobalId | null, doSearch: boolean = true) {
    this.staticFetcher.setParentGlobalId(parentGlobalId);
    this.dynamicFetcher.setParentGlobalId(parentGlobalId);
    this.cacheFetcher.setParentGlobalId(parentGlobalId);
    if (doSearch) this.performSearch();
  }

  get benchSearch(): boolean {
    return globalIdPatterns.bench.test(this.fetcher.parentGlobalId ?? "");
  }

  onUsersBench(user: Person): boolean {
    return (
      this.benchSearch &&
      this.fetcher.parentGlobalId === `BE${user.workbenchId}`
    );
  }

  /*
   * In the process of performing some search task, some asynchronous step
   * (such as network activity) may be required during which the user could
   * perform an action that negates the need to perform any further logic and
   * instead the current task should be cancelled. This method compares the
   * search parameters of the current search before and after such asynchronous
   * activity and should the search parameters have changed, because the user
   * took some other action, the returned promise will be rejected rather than
   * resolve.
   */
  async rejectIfSearchParametersChange<T>(promise: Promise<T>): Promise<T> {
    const searchParameterBefore = this.fetcher.serialize;
    const resolved = await promise;
    const searchParameterAfter = this.fetcher.serialize;
    if (!sameKeysAndValues(searchParameterBefore, searchParameterAfter))
      throw new InvalidState(
        "Search parameters have changed, cancelling search."
      );
    return resolved;
  }

  /*
   * Before calling performInitialSearch this method does some setup work by
   * clearing the fetchers and setting the owner and bench based on the passed
   * parameters. This setup work requires some asynchronous operations and as
   * such, this method also handles the case where the search parameters of the
   * current fetcher are modified during those operations and will simply
   * resolve without mutating any state when that occurs.
   */
  async setupAndPerformInitialSearch(params: CoreFetcherArgs): Promise<void> {
    this.dynamicFetcher = new DynamicFetcher(this.factory, params);
    this.staticFetcher = new CoreFetcher(this.factory, params);
    this.cacheFetcher = new CacheFetcher(this.factory, params);
    this.fetcher.setLoading(true);
    const { peopleStore } = getRootStore();

    if (params.ownedBy) {
      try {
        const person = await this.rejectIfSearchParametersChange(
          peopleStore.getUser(params.ownedBy)
        );
        this.setOwner(person ?? null, false);
      } catch (e) {
        if (e instanceof InvalidState) return;
        throw e;
      } finally {
        this.fetcher.setLoading(false);
      }
    }

    if (params.parentGlobalId) {
      const parentGlobalId = params.parentGlobalId;
      if (/^BE/.test(parentGlobalId)) {
        const id = /BE(?<id>.*)/.exec(parentGlobalId)?.groups?.id;
        if (id) {
          try {
            const person = await this.rejectIfSearchParametersChange(
              peopleStore.getPersonFromBenchId(parseInt(id, 10))
            );
            this.setBench(person, false);
          } catch (e) {
            if (e instanceof InvalidState) return;
            throw e;
          } finally {
            this.fetcher.setLoading(false);
          }
        }
      }
    }

    return this.fetcher.performInitialSearch(omitNull(params));
  }

  get allowedStatusFilters(): RsSet<DeletedItems> {
    if (this.fetcher.parentIsContainer || this.benchSearch)
      return new RsSet(["EXCLUDE"]);
    return new RsSet(["EXCLUDE", "INCLUDE", "DELETED_ONLY"]);
  }

  get allowedTypeFilters(): AllowedTypeFilters {
    if (!this.uiConfig.allowedTypeFilters) return new Set([]);
    const allowedTypeFilters = new Set([...this.uiConfig.allowedTypeFilters]);
    if (this.benchSearch || this.fetcher.parentIsContainer) {
      allowedTypeFilters.delete("TEMPLATE");
      allowedTypeFilters.delete("SAMPLE");
    }
    if (this.fetcher.parentIsSample) {
      allowedTypeFilters.delete("CONTAINER");
      allowedTypeFilters.delete("SAMPLE");
      allowedTypeFilters.delete("TEMPLATE");
    }
    if (this.fetcher.parentIsTemplate) {
      allowedTypeFilters.delete("CONTAINER");
      allowedTypeFilters.delete("SUBSAMPLE");
      allowedTypeFilters.delete("TEMPLATE");
    }
    if (!this.fetcher.allTypesAllowed) {
      allowedTypeFilters.delete("ALL");
    }
    return allowedTypeFilters;
  }

  get loading(): boolean {
    return this.processingContextActions || this.fetcher.loading;
  }

  get batchEditableInstance(): Editable {
    if (!this.batchEditingRecords)
      throw new Error("Batch editing is not enabled.");
    const records: Array<InventoryRecord> = this.batchEditingRecords.toArray();
    if (records.length === 0) throw new Error("Nothing selected.");
    const { uiStore } = getRootStore();

    return {
      loading: this.editLoading === "batch",
      cancel: () =>
        showToastWhilstPending<void>(
          "Cancelling...",
          new Promise((resolve) =>
            (async () => {
              this.editLoading = "batch";
              await Promise.all(records.map((r) => r.cancel()));
              await this.setActiveResult();
              this.editLoading = "no";
            })().then(resolve)
          )
        ),
      update: async () => {
        this.editLoading = "batch";
        try {
          const {
            data: { results, errorCount },
          } = await ApiService.bulk<{
            results: Array<{
              error: { errors: Array<string> };
              record: { globalId: GlobalId | null };
            }>;
            errorCount: number;
          }>(
            [
              ...records.map(
                (r) =>
                  ({
                    ...r.paramsForBackend,
                    type: r.type,
                  } as BulkEndpointRecordSerialisation)
              ),
            ],
            "UPDATE",
            false
          );
          if (
            handleDetailedErrors(
              errorCount,
              ArrayUtils.zipWith(results, records, (d, r) => ({
                response: d,
                record: r,
              })),
              "update"
            )
          ) {
            return;
          }
          const newRecordData: Record<GlobalId, any> = Object.fromEntries(
            results.map(({ record }) => [record.globalId, record])
          );
          const factory = this.factory.newFactory();
          for (const r of records) {
            if (r.globalId === null) continue;
            r.populateFromJson(factory, newRecordData[r.globalId], null);
          }
          handleDetailedSuccesses(records, "updated");
          await Promise.all(records.map((r) => r.cancel()));
          this.disableBatchEditing();
          void this.setActiveResult();
        } catch (error) {
          uiStore.addAlert(
            mkAlert({
              title: "Update failed.",
              message: getErrorMessage(error, "Unknown reason"),
              variant: "error",
            })
          );
          console.error("Could not perform update.", error);
        } finally {
          this.editLoading = "no";
        }
      },
      uploadProgress: noProgress,
      submittable: allAreValid([
        this.editLoading === "no" ? IsValid() : IsInvalid("Loading."),
        ...records.map((r) => r.submittable),
        this.batchEditingRecords?.some(
          (r) => r.currentlyEditableFields.size > 0
        )
          ? IsValid()
          : IsInvalid("No fields have been edited."),
      ]),
    };
  }

  /*
   * After performing certain operations, it is necessary to identify if any of
   * the records being operated on have a template that is the current active
   * result, because if it is then we typically want to perform some refresh
   * action. Samples without a template are treated as if their template were
   * not the active result.
   *
   * The method takes a type parameter that is a subtype of InventoryRecord so
   * that we can signify to Flow that the `records` argument will not be
   * mutated in any way. This way, we can pass objects of any type that
   * implements InventoryRecord in test code, without worrying that this method
   * might add elements to the set that would violate the subtype invariant.
   */
  isActiveResultTemplateOfAny<T extends InventoryRecord>(
    records: RsSet<T>
  ): boolean {
    const activeResultGlobalId =
      getRootStore().searchStore.activeResult?.globalId;
    for (const sample of records.filterClass(SampleModel)) {
      const templateId = sample.templateId;
      if (!templateId) continue;
      if (activeResultGlobalId === `IT${templateId}`) return true;
    }
    return false;
  }

  currentBasket(baskets: ReadonlyArray<Basket>): Basket | undefined {
    return baskets.find((b) => b.globalId === this.fetcher.parentGlobalId);
  }
}
