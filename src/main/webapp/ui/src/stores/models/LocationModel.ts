import getRootStore from "../stores/RootStore";
import ContainerModel from "./ContainerModel";
import InventoryBaseRecord from "./InventoryBaseRecord";
import SubSampleModel from "./SubSampleModel";
import { action, computed, observable, makeObservable } from "mobx";
import { type Location, type Container } from "../definitions/Container";
import { type SubSample } from "../definitions/SubSample";
import { type Search } from "../definitions/Search";
import { type InventoryRecord } from "../definitions/InventoryRecord";
import { type HasLocation } from "../definitions/HasLocation";

export type LocationAttrs = {
  id: number | null;
  coordX: number;
  coordY: number;
  content: SubSampleModel | ContainerModel | null;
  parentContainer: ContainerModel;
};

export default class LocationModel implements Location {
  loading: boolean = false;
  id: number | null = null;
  // @ts-expect-error Set by constructor's call to setAttributes
  coordX: number;
  // @ts-expect-error Set by constructor's call to setAttributes
  coordY: number;
  // @ts-expect-error Set by constructor's call to setAttributes
  content: null | (InventoryRecord & HasLocation);
  selected: boolean = false;
  // @ts-expect-error Set by constructor's call to setAttributes
  parentContainer: Container;
  x: number = 0;
  y: number = 0;
  width: number = 0;
  height: number = 0;

  constructor(params: LocationAttrs) {
    makeObservable(this, {
      loading: observable,
      id: observable,
      coordX: observable,
      coordY: observable,
      content: observable,
      selected: observable,
      parentContainer: observable,
      x: observable,
      y: observable,
      setAttributes: action,
      toggleSelected: action,
      selectOnlyThis: action,
      setPosition: action,
      setDimensions: action,
      siblings: computed,
      isSiblingSelected: computed,
      allSiblingsSelected: computed,
      name: computed,
      paramsForBackend: computed,
      hasContent: computed,
      uniqueColor: computed,
    });
    this.setAttributes(params);
  }

  /*
   * When the user performs a search within a container or applies a filter
   * e.g. based on ownership, the locations that don't apply get greyed-out to
   * highlight those that do apply
   */
  isGreyedOut(search: Search): boolean {
    const parentSearch = this.parentContainer.contentSearch;
    if (
      search === parentSearch &&
      (parentSearch.fetcher.query ||
        parentSearch.fetcher.resultType !== "ALL" ||
        parentSearch.fetcher.owner)
    ) {
      // search inside container is being performed
      if (!this.content) return true;
      const content = this.content;
      return !search.isInResults(content) || search.alwaysFilterOut(content);
    }
    // search inside container is not being performed i.e. move or create dialogs
    return (
      this.content instanceof InventoryBaseRecord && search.alwaysFilterOut(this.content)
    );
  }

  isShallow(search: Search): boolean {
    if (!search.uiConfig.onlyAllowSelectingEmptyLocations) {
      /*
       * Outside of the move dialog, allow empty locations to be selected.
       * This way, you can clear a selection of empty locations (typically
       * created by drag-and-drop) by dragging the selection dragger over the
       * table/image, whilst still being able to quickly select just the filled
       * locations by dragging over all of the locations, filled or not.
       */
      if (!this.content && !this.selected) return false;
    }
    if (this.isGreyedOut(search)) return false;

    const cont = this.parentContainer;
    if (!cont.selectionMode || !cont.selectionStart || !cont.selectionEnd) {
      return false;
    }

    const topLeftX = Math.min(cont.selectionStart.x, cont.selectionEnd.x),
      topLeftY = Math.min(cont.selectionStart.y, cont.selectionEnd.y),
      bottomRightX = Math.max(cont.selectionStart.x, cont.selectionEnd.x),
      bottomRightY = Math.max(cont.selectionStart.y, cont.selectionEnd.y);

    return (
      this.y + this.height >= topLeftY &&
      this.y <= bottomRightY &&
      this.x + this.width >= topLeftX &&
      this.x <= bottomRightX
    );
  }

  isShallowSelected(search: Search): boolean {
    return this.isShallow(search) && !this.selected;
  }

  isShallowUnselected(search: Search): boolean {
    return this.isShallow(search) && this.selected;
  }

  get siblings(): Array<Location> {
    if (
      !this.parentContainer ||
      !this.content ||
      !(this.content instanceof SubSampleModel) ||
      !this.content.sample.id
    )
      return [];
    if (!this.parentContainer.locations)
      throw new Error("Locations of container must be known.");
    return this.parentContainer.locations.filter(
      (loc) =>
        loc.content instanceof SubSampleModel &&
        Boolean(loc.content.sample.id) &&
        this.content instanceof SubSampleModel &&
        loc.content.sample.id === this.content.sample.id
    );
  }

  get isSiblingSelected(): boolean | null {
    return (
      this.content &&
      this.content.recordType === "subSample" &&
      this.siblings.some((loc) => loc.selected)
    );
  }

  get allSiblingsSelected(): boolean {
    return this.siblings.every((loc) => loc.selected);
  }

  isSelectable(search: Search): boolean {
    if (search.uiConfig.onlyAllowSelectingEmptyLocations)
      return !this.hasContent;
    return !this.isGreyedOut(search);
  }

  get name(): string | null {
    return this.content === null ? null : this.content.name;
  }

  get paramsForBackend(): object {
    const params: {
      parentContainer: unknown;
      content?: SubSample | Container | null;
    } = { ...this };
    delete params.parentContainer;
    delete params.content;
    return params;
  }

  get hasContent(): boolean {
    return Boolean(
      this.content && this.content.globalId && !this.content.deleted
    );
  }

  get uniqueColor(): string {
    if (this.content instanceof SubSampleModel) {
      const uniqueColor = this.parentContainer.getColor(this.content.sample.id);
      if (!uniqueColor)
        throw new Error(
          "Impossible state: sample of content's subsample should always have a valid colour within the parent container"
        );
      return uniqueColor;
    }
    return "white";
  }

  setAttributes(params: LocationAttrs) {
    Object.assign(this, params);
  }

  /*
   * Set the `selected` status of this record and synchronise the selection
   * with the searchStore's activeResult's `contentSearch`, if the activeResult
   * is a Container. This is so that a selection made in grid or image view is
   * maintained when switching to list view.
   */
  toggleSelected(value?: boolean | null): void {
    value = value ?? !this.selected;
    const moveStore = getRootStore().moveStore;
    if (
      moveStore.isMoving &&
      moveStore.activeResult instanceof ContainerModel
    ) {
      const activeResult: ContainerModel = moveStore.activeResult;
      if (!activeResult.selectedLocations)
        throw new Error("Locations of container must be known.");
      const selectedLocations = activeResult.selectedLocations;
      if (value) {
        const selectedGlobalIds = selectedLocations.map(
          (l) => l.content?.globalId
        );
        const firstNotPlaced =
          moveStore.selectedResults.find(
            (r) => !selectedGlobalIds.includes(r.globalId)
          ) ?? null;

        // set new location
        this.content = firstNotPlaced as Container | SubSample | null;
      } else {
        this.content = null;
      }
    }

    this.selected = value;

    // Sync selection of location and content
    if (this.parentContainer.cType !== "LIST" && !moveStore.isMoving) {
      const activeContainer = getRootStore().searchStore.activeResult;
      if ((activeContainer as Container | null)?.contentSearch) {
        const content = (
          activeContainer as Container
        ).contentSearch.cacheFetcher.results.find(
          (r) => r.globalId === this.content?.globalId
        );
        if (content) content.toggleSelected(value);
      }
    }
  }

  /*
   * Set the selected location,
   * unselecting other locations first (for creation)
   */
  selectOnlyThis() {
    this.parentContainer.toggleAllLocations(false);
    this.toggleSelected(true);
  }

  setPosition(x: number, y: number) {
    this.x = x;
    this.y = y;
  }

  setDimensions(width: number, height: number) {
    this.width = width;
    this.height = height;
  }
}
