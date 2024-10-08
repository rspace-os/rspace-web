// @flow

import ApiService from "../../common/InvApiService";
import { type Container, type WorkbenchId } from "../definitions/Container";
import { type Id, type GlobalId } from "../definitions/BaseRecord";
import { type Factory } from "../definitions/Factory";
import ContainerModel, { type ContainerAttrs } from "../models/ContainerModel";
import {
  type CoreFetcherArgs,
  type CoreFetcher,
  type ResultType,
} from "../definitions/Search";
import Result from "../models/Result";
import SampleModel from "../models/SampleModel";
import Search from "../models/Search";
import { type Basket, type BasketAttrs } from "../definitions/Basket";
import BasketModel from "../models/Basket";
import TemplateModel, { type TemplateAttrs } from "../models/TemplateModel";
import MemoisedFactory from "../models/Factory/MemoisedFactory";
import { type RootStore } from "./RootStore";
import {
  runInAction,
  action,
  computed,
  observable,
  makeObservable,
} from "mobx";
import { type InventoryRecord } from "../definitions/InventoryRecord";
import { type Group } from "../definitions/Group";
import { mkAlert } from "../contexts/Alert";
import { showToastWhilstPending } from "../../util/alerts";
import React from "react";

export type SavedSearch = {|
  ...CoreFetcherArgs,
  name: string,
|};

export type NewInContainerParams = {
  parentContainers: Array<ContainerModel>,
  /*
   * An empty object is used to denote that a location does not need to be
   * specified when the parent container is a list container
   */
  parentLocation: | {|
        coordX: number,
        coordY: number,
      |}
    | {||},
};

const SAVED_SEARCHES: ?Array<SavedSearch> = JSON.parse(
  // $FlowExpectedError[incompatible-call]
  localStorage.getItem("searches")
);

/*
 * Over time the format of saved searches has changed and this function
 * ensures that no matter what format the user has saved in their browser,
 * they are made compatible with the latest version.
 */
const normaliseSavedSearches = (
  searches: Array<SavedSearch>
): Array<SavedSearch> => {
  return searches.map((search) => ({
    ...search,
    name: search.name ?? search.query,
  }));
};

export const getContainer = async (id: Id): Promise<ContainerModel> => {
  if (id) {
    const { data } = await ApiService.get<void, ContainerAttrs>(
      "containers",
      id
    );
    return new ContainerModel(new MemoisedFactory(), data);
  }
  throw new Error("Cannot get container without id");
};

export default class SearchStore {
  rootStore: RootStore;
  search: Search;
  savedSearches: Array<SavedSearch>;
  savedBaskets: Array<BasketModel>;
  error: string;

  constructor(rootStore: RootStore) {
    makeObservable(this, {
      search: observable,
      savedSearches: observable,
      savedBaskets: observable,
      error: observable,
      saveSearch: action,
      deleteSearch: action,
      getBaskets: action,
      getBasket: action,
      createBasket: action,
      deleteBasket: action,
      createNew: action,
      results: computed,
      fetcher: computed,
      activeResult: computed,
      searchIsSaved: computed,
      activeResultIsBeingEdited: computed,
    });
    this.rootStore = rootStore;
    this.search = new Search({
      factory: new MemoisedFactory(),
      callbacks: {
        setActiveResult: (r) => r?.refreshAssociatedSearch(),
      },
    });
    this.search.canEditActiveResult = true;
    this.savedSearches = normaliseSavedSearches(SAVED_SEARCHES ?? []);
    this.savedBaskets = [];
  }

  get results(): Array<InventoryRecord> {
    return this.search.filteredResults;
  }

  get fetcher(): CoreFetcher {
    return this.search.fetcher;
  }

  get activeResult(): ?InventoryRecord {
    return this.search.activeResult;
  }

  get searchIsSaved(): boolean {
    return (
      this.savedSearches.findIndex(
        (savedSearch) =>
          JSON.stringify(savedSearch) ===
          JSON.stringify(this.search.fetcher.serialize)
      ) !== -1
    );
  }

  /*
   * Save the current search parameters as a saved search, or rename an
   * existing saved search. Saved searches are stored in the browser's
   * localStorage so this method has the side effect of mutating that state.
   *
   * @param name The name for the new search/new name for the existing saved
   *             search
   * @param index If null then a new search is created with the current search
   *              parameters. If a valid index of the `savedSearches` array
   *              then the saved search at that index is renamed. Any other
   *              value will cause an error.
   */
  saveSearch(name: string, index?: number): void {
    if (this.savedSearches.map((s) => s.name).includes(name))
      throw new Error(`"${name}" already exists`);
    if (typeof index === "number") {
      const updatedSearch = { ...this.savedSearches[index], name };
      this.savedSearches.splice(index, 1, updatedSearch);
      localStorage.setItem("searches", JSON.stringify(this.savedSearches));
    } else {
      const newSearch = {
        ...this.search.fetcher.serialize,
        name,
      };
      this.savedSearches.push(newSearch);
      localStorage.setItem("searches", JSON.stringify(this.savedSearches));
    }
  }

  deleteSearch(search: SavedSearch) {
    this.savedSearches = this.savedSearches.filter(
      (savedSearch) => JSON.stringify(savedSearch) !== JSON.stringify(search)
    );
    localStorage.setItem("searches", JSON.stringify(this.savedSearches));
  }

  async getBaskets(): Promise<void> {
    const { uiStore } = this.rootStore;
    try {
      const { data } = await ApiService.get<void, Array<BasketAttrs>>(
        "baskets"
      );
      runInAction(() => {
        this.savedBaskets = data.map(
          (basketAttrs) => new BasketModel(basketAttrs)
        );
      });
    } catch (e) {
      uiStore.addAlert(
        mkAlert({
          title: "Error retrieving Baskets.",
          message: e.message || "",
          variant: "error",
          isInfinite: true,
        })
      );
    }
  }

  async getBasket(id: Id): Promise<Basket | void> {
    const { uiStore } = this.rootStore;
    try {
      if (id) {
        const { data } = await ApiService.get<void, BasketAttrs>(
          `baskets/${id}`
        );
        if (data) return new BasketModel(data);
      } else throw new Error("Cannot retrieve Basket without id");
    } catch (e) {
      uiStore.addAlert(
        mkAlert({
          title: `Error retrieving Basket.`,
          message: e.message || "",
          variant: "error",
          isInfinite: true,
        })
      );
    }
  }

  async createBasket(name?: string, items?: Array<GlobalId>): Promise<void> {
    const { uiStore } = this.rootStore;
    try {
      const res = await showToastWhilstPending(
        "Creating Basket...",
        ApiService.post<
          {| name: string, globalIds: Array<GlobalId> |},
          BasketAttrs
        >("baskets", { name, globalIds: items })
      );
      if (res.status === 201 && res.data) {
        // refetch to update list
        await this.getBaskets();
        uiStore.addAlert(
          mkAlert({
            title: `${res.data.name} has been created.`,
            message: items ? "The selected items have been added to it." : "",
            variant: "success",
            isInfinite: false,
          })
        );
      }
    } catch (e) {
      uiStore.addAlert(
        mkAlert({
          title: "Error creating Basket.",
          message: e.message || "",
          variant: "error",
          isInfinite: true,
        })
      );
    }
  }

  async deleteBasket(id: Id): Promise<void> {
    const { uiStore } = this.rootStore;
    try {
      if (id) {
        const name =
          this.savedBaskets.find((b) => b.id === id)?.name ?? "The Basket";
        if (
          await uiStore.confirm(
            `Deleting Basket`,
            <>
              You are about to delete {name}.
              <br />
              The contents of the Basket will not be affected.
            </>,
            "OK",
            "CANCEL"
          )
        ) {
          const res = await showToastWhilstPending(
            "Deleting Basket...",
            ApiService.delete<void, void>(`baskets`, id)
          );
          if (res.status === 200) {
            // refetch to update list
            await this.getBaskets();
            uiStore.addAlert(
              mkAlert({
                title: `${name} has been deleted.`,
                message: "Its contents have not been affected.",
                variant: "success",
                isInfinite: false,
              })
            );
          }
        }
      } else {
        throw new Error("Cannot delete Basket without id");
      }
    } catch (e) {
      uiStore.addAlert(
        mkAlert({
          title: "Error deleting Basket.",
          message: e.message || "",
          variant: "error",
          isInfinite: true,
        })
      );
    }
  }

  async createNewHelper(newRecord: Result): Promise<void> {
    await this.search.setActiveResult(newRecord);
    this.rootStore.uiStore.setVisiblePanel("right");
  }

  async createNewSample(
    subsampleParentDetails?: NewInContainerParams
  ): Promise<SampleModel> {
    const currentUsersGroups =
      await this.rootStore.peopleStore.fetchCurrentUsersGroups();
    const sample = new SampleModel(new MemoisedFactory());
    sample.setAttributes({
      ...(subsampleParentDetails
        ? {
            newSampleSubSampleTargetLocations: [
              {
                containerId: subsampleParentDetails.parentContainers[0].id,
                location: subsampleParentDetails.parentLocation,
              },
            ],
          }
        : {}),
      sharedWith: currentUsersGroups.map((group) => ({
        group,
        shared: true,
        itemOwnerGroup: true,
      })),
    });
    await this.createNewHelper(sample);
    return sample;
  }

  async createNewContainer(
    containerParentDetails?: NewInContainerParams
  ): Promise<ContainerModel> {
    const [currentUsersBench, currentUsersGroups] = await Promise.all<
      [?Promise<Container>, Promise<Array<Group>>]
    >([
      this.rootStore.peopleStore.currentUser?.getBench(),
      this.rootStore.peopleStore.fetchCurrentUsersGroups(),
    ]);
    const container = new ContainerModel(new MemoisedFactory());
    const locationIsDefined: ?boolean =
      containerParentDetails &&
      Object.keys(containerParentDetails.parentLocation).length > 0;

    container.setAttributes({
      parentContainers: containerParentDetails?.parentContainers ?? [
        currentUsersBench,
      ],
      ...(containerParentDetails && locationIsDefined
        ? { parentLocation: containerParentDetails.parentLocation }
        : {}),
      sharedWith: currentUsersGroups.map((group) => ({
        group,
        shared: true,
        itemOwnerGroup: true,
      })),
    });
    await this.createNewHelper(container);
    return container;
  }

  async createNewTemplate(): Promise<TemplateModel> {
    const currentUsersGroups =
      await this.rootStore.peopleStore.fetchCurrentUsersGroups();
    const template = new TemplateModel(new MemoisedFactory());
    template.setAttributes({
      sharedWith: currentUsersGroups.map((group) => ({
        group,
        shared: true,
        itemOwnerGroup: true,
      })),
    });
    await this.createNewHelper(template);
    return template;
  }

  createNew(type: "sample" | "container" | "template"): Promise<Result> {
    if (type === "sample") return this.createNewSample();
    if (type === "container") return this.createNewContainer();
    return this.createNewTemplate();
  }

  async getBench(workbenchId: WorkbenchId): Promise<Container> {
    const { data } = await ApiService.get<void, ContainerAttrs>(
      "workbenches",
      workbenchId
    );
    return new ContainerModel(new MemoisedFactory(), data);
  }

  async getTemplate(
    id: number,
    version: ?number,
    factory: Factory
  ): Promise<TemplateModel> {
    const { data } = await ApiService.get<void, TemplateAttrs>(
      "sampleTemplates",
      typeof version === "number" ? `${id}/versions/${version}` : `${id}`
    );
    return new TemplateModel(factory, data);
  }

  get activeResultIsBeingEdited(): boolean {
    return this.activeResult?.editing ?? false;
  }

  isTypeSelected(resultType: ResultType): boolean {
    return (
      !this.search.fetcher.parentGlobalId &&
      this.search.fetcher.resultType === resultType
    );
  }
}
