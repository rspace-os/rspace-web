// @flow

import {
  action,
  observable,
  computed,
  makeObservable,
  runInAction,
} from "mobx";
import ApiService, {
  type BulkEndpointRecordSerialisation,
} from "../../common/InvApiService";
import type { RootStore } from "./RootStore";
import ContainerModel from "../models/ContainerModel";
import SubSampleModel from "../models/SubSampleModel";
import SampleModel from "../models/SampleModel";
import { mkAlert } from "../contexts/Alert";
import {
  handleDetailedErrors,
  handleDetailedSuccesses,
} from "../../util/alerts";
import MemoisedFactory from "../models/Factory/MemoisedFactory";
import Search from "../models/Search";
import Result from "../models/Result";
import { type GlobalId, getSavedGlobalId } from "../definitions/BaseRecord";
import { type Container, type Location } from "../definitions/Container";
import {
  type InventoryRecord,
  recordTypeToApiRecordType,
} from "../definitions/InventoryRecord";
import { type Panel } from "../../util/types";
import { Optional } from "../../util/optional";

type SerialisedRecord =
  | {|
      ...$Exact<BulkEndpointRecordSerialisation>,
      globalId: ?GlobalId,
      removeFromParentContainerRequest: true,
    |}
  | {|
      ...$Exact<BulkEndpointRecordSerialisation>,
      globalId: ?GlobalId,
      parentContainers: $ReadOnlyArray<{ ... }>,
      parentLocation?: { ... },
    |};

export default class MoveStore {
  rootStore: RootStore;
  loading: boolean = false;
  isMoving: boolean = false;
  submitting: "NO" | "MAKE-TOP" | "TO-OTHER" = "NO";
  selectedResults: Array<ContainerModel | SubSampleModel> = [];
  search: ?Search;

  constructor(rootStore: RootStore) {
    makeObservable(this, {
      loading: observable,
      isMoving: observable,
      submitting: observable,
      selectedResults: observable,
      search: observable,
      setIsMoving: action,
      setTargetContainer: action,
      setSelectedResults: action,
      moveSelected: action,
      moveRecords: action,
      resetGrid: action,
      setActivePane: action,
      results: computed,
      activeResult: computed,
      targetLocations: computed,
      selectedResultsIncludesContainers: computed,
      selectedResultsIncludesSubSamples: computed,
      globalIdsOfSelectedResults: computed,
      sourceIsAlsoDestination: computed,
    });
    this.rootStore = rootStore;
  }

  get results(): ?Array<InventoryRecord> {
    return this.search?.filteredResults;
  }

  get activeResult(): ?Container {
    return (
      // $FlowExpectedError[prop-missing] Move's search's activeResult will always be a container
      this.search?.activeResult ?? this.rootStore.peopleStore.currentUser?.bench
    );
  }

  get targetLocations(): ?Array<Location> {
    return this.activeResult?.selectedLocations;
  }

  get selectedResultsIncludesContainers(): boolean {
    return this.selectedResults.some((r) => r.recordType === "container");
  }

  get selectedResultsIncludesSubSamples(): boolean {
    return this.selectedResults.some((r) => r.recordType === "subSample");
  }

  async setIsMoving(val: boolean) {
    this.isMoving = val;
    this.setActivePane("left");

    if (this.isMoving) {
      const search = new Search({
        fetcherParams: {
          resultType: "CONTAINER",
        },
        treeArgs: {
          filteredTypes: ["container"],
        },
        uiConfig: {
          allowedTypeFilters: new Set(["CONTAINER"]),
          selectionMode: "SINGLE",
        },
        callbacks: {
          setActiveResult: (r) => {
            if (r && r instanceof ContainerModel) {
              r.refreshAssociatedSearch();
              this.clearLocationsWithContentBeingMovedOut(r);
              r.contentSearch.uiConfig.selectionMode =
                !r.canStoreRecords ||
                r.cType === "LIST" ||
                r.cType === "WORKBENCH"
                  ? "NONE"
                  : "MULTIPLE";
            }
          },
        },
        factory: new MemoisedFactory(),
      });
      this.search = search;

      void search.setSearchView("TREE");
      await this.setTargetContainer();
    }
  }

  async setTargetContainer(c: ?Container) {
    const container = c ?? this.rootStore.peopleStore.currentUser?.bench;
    if (this.search) {
      try {
        await this.search.setActiveResult(container);
      } catch (e) {
        console.error("Could not set target container", e);
      }
    }
  }

  clearLocationsWithContentBeingMovedOut(container: Container): void {
    if (!container.locations)
      throw new Error("Locations of container must be known.");
    const locations = container.locations;
    if (this.search) {
      for (const loc of locations) {
        if (
          this.selectedResults
            .map((sr) => sr.globalId)
            .includes(loc.content?.globalId)
        ) {
          loc.content = null;
        }
      }
    }
  }

  setSelectedResults(records: Array<InventoryRecord>) {
    this.selectedResults = [];
    /*
     * we have to remove sample objects...
     * ... and include their aliquots
     */
    records.map(async (record) => {
      if (record instanceof SampleModel) {
        if (!record.infoLoaded) {
          await (record.fetchAdditionalInfo(): void);
        }
        this.selectedResults = [...this.selectedResults, ...record.subSamples];
      } else if (
        record instanceof SubSampleModel ||
        record instanceof ContainerModel
      ) {
        this.selectedResults = [...this.selectedResults, record];
      } else {
        throw new Error("Unknown type.");
      }
    });
  }

  async moveSelected(removeParentContainers: boolean = false): Promise<void> {
    this.submitting = removeParentContainers ? "MAKE-TOP" : "TO-OTHER";

    let serialisedRecords: Array<SerialisedRecord>;
    if (removeParentContainers) {
      serialisedRecords = this.selectedResults.map((r) => ({
        id: r.id,
        type: recordTypeToApiRecordType(r.recordType),
        globalId: r.globalId,
        removeFromParentContainerRequest: true,
      }));
    } else {
      if (!(this.activeResult instanceof ContainerModel))
        throw new Error("Active result not set.");
      const cType = this.activeResult.cType;
      const infiniteContainer = cType === "LIST" || cType === "WORKBENCH";
      const parentContainers = [this.activeResult.paramsForBackend];

      if (infiniteContainer) {
        serialisedRecords = this.selectedResults.map((r) => ({
          id: r.id,
          type: recordTypeToApiRecordType(r.recordType),
          globalId: r.globalId,
          parentContainers,
        }));
      } else {
        if (!this.targetLocations)
          throw new Error("Could not get target locations.");
        if (this.targetLocations.length === 0)
          throw new Error("Nothing is selected.");

        serialisedRecords = this.targetLocations.map((loc) => {
          if (!loc.content)
            throw new Error("An empty location has been selected.");
          const content = loc.content;
          return {
            id: content.id,
            type: recordTypeToApiRecordType(content.recordType),
            globalId: content.globalId,
            parentContainers,
            parentLocation: loc.paramsForBackend,
          };
        });
      }
    }

    await this.moveRecords(serialisedRecords);
    await this.refreshAfterMove();
  }

  async moveRecords(records: $ReadOnlyArray<SerialisedRecord>): Promise<void> {
    try {
      const { data } = await ApiService.bulk<
        mixed,
        {
          successCount: number,
          results: Array<{ error: { errors: Array<string> }, record: any }>,
          errorCount: number,
        }
      >(records, "MOVE", true);
      const factory = new MemoisedFactory();
      const results: Array<InventoryRecord> = data.results
        .filter((r) => Boolean(r.record))
        .map((r) => {
          const newRecord = factory.newRecord(r.record);
          newRecord.populateFromJson(factory, r.record);
          return newRecord;
        });

      if (
        handleDetailedErrors(
          data.errorCount,
          data.results.map((r) => ({ response: r })),
          "move"
        )
      ) {
        return;
      }
      handleDetailedSuccesses(results, "moved");

      if (data.successCount) {
        this.rootStore.trackingStore.trackEvent("InventoryItemMoved", {
          movedItemsCount: data.successCount,
          targetCType: results.map((r) => r.paramsForBackend)[0]
            .parentContainers?.[0]?.cType,
        });
      }
      void this.setIsMoving(false);
    } catch (error) {
      this.rootStore.uiStore.addAlert(
        mkAlert({
          title: "Move failed.",
          message:
            error?.response?.data.message ?? error.message ?? "Unknown reason.",
          variant: "error",
        })
      );
      console.error("Could not move records", error);
      throw error;
    } finally {
      runInAction(() => {
        this.submitting = "NO";
      });
    }
  }

  resetGrid() {
    this.selectedResults = [];
    void this.setTargetContainer();
  }

  get globalIdsOfSelectedResults(): Set<GlobalId> {
    return new Set(this.selectedResults.map(getSavedGlobalId));
  }

  isRecordMoving(record: Result): boolean {
    if (!record.globalId) return false;
    return this.globalIdsOfSelectedResults.has(record.globalId);
  }

  async refreshAfterMove() {
    const { searchStore, peopleStore } = this.rootStore;
    void searchStore.search.fetcher.performInitialSearch();
    const activeResult = searchStore.activeResult;
    if (activeResult) {
      if (activeResult.state === "preview") {
        await activeResult.fetchAdditionalInfo();
      }
      activeResult.refreshAssociatedSearch();
    }
    if (peopleStore.currentUser) void peopleStore.currentUser.getBench();
  }

  setActivePane(panel: Panel) {
    this.rootStore.uiStore.setDialogVisiblePanel(panel);
  }

  get sourceIsAlsoDestination(): boolean {
    return Optional.fromNullable(this.search?.activeResult?.globalId)
      .map((destGlobalId) => {
        const sources = this.selectedResults.map(
          (r) => r.immediateParentContainer
        );
        return sources.every((s) => s?.globalId === destGlobalId);
      })
      .orElse(false);
  }
}
