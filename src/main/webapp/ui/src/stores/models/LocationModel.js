// @flow

import getRootStore from "../stores/RootStore";
import ContainerModel from "./ContainerModel";
import Result from "./Result";
import SubSampleModel from "./SubSampleModel";
import { action, computed, observable, makeObservable } from "mobx";
import { type Location, type Container } from "../definitions/Container";
import { type SubSample } from "../definitions/SubSample";

export type LocationAttrs = {|
  id: ?number,
  coordX: number,
  coordY: number,
  content: ?(SubSampleModel | ContainerModel),
  parentContainer: ContainerModel,
|};

export default class LocationModel implements Location {
  loading: boolean = false;
  id: ?number = null;
  coordX: number;
  coordY: number;
  content: ?(SubSample | Container);
  selected: boolean = false;
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
      isGreyedOut: computed,
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
  get isGreyedOut(): boolean {
    if (getRootStore().createStore.creationContext) return this.hasContent;
    const search = this.parentContainer.contentSearch;
    if (
      search.fetcher.query ||
      search.fetcher.resultType !== "ALL" ||
      search.fetcher.owner
    ) {
      // search inside container is being performed
      if (!this.content) return true;
      const content = this.content;
      return !search.isInResults(content) || search.alwaysFilterOut(content);
    }
    // search inside container is not being performed
    return (
      this.content instanceof Result && search.alwaysFilterOut(this.content)
    );
  }

  isShallow(opts: {| onlyAllowSelectingEmptyLocations: boolean |}): boolean {
    if (!opts.onlyAllowSelectingEmptyLocations) {
      /*
       * Outside of the move dialog, allow empty locations to be selected.
       * This way, you can clear a selection of empty locations (typically
       * created by drag-and-drop) by dragging the selection dragger over the
       * table/image, whilst still being able to quickly select just the filled
       * locations by dragging over all of the locations, filled or not.
       */
      if (!this.content && !this.selected) return false;
    }
    if (this.isGreyedOut) return false;

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

  isShallowSelected(opts: {|
    onlyAllowSelectingEmptyLocations: boolean,
  |}): boolean {
    return this.isShallow(opts) && !this.selected;
  }

  isShallowUnselected(opts: {|
    onlyAllowSelectingEmptyLocations: boolean,
  |}): boolean {
    return this.isShallow(opts) && this.selected;
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
    const locations = this.parentContainer.locations;
    return locations
      .filter(
        ({ content }) =>
          content instanceof SubSampleModel && Boolean(content.sample.id)
      )
      .filter(
        // $FlowExpectedError[incompatible-use] this.content.sample.id !== null is asserted above
        // $FlowExpectedError[prop-missing]
        (loc) => loc.content.sample.id === this.content.sample.id
      );
  }

  get isSiblingSelected(): ?boolean {
    return (
      this.content &&
      this.content.recordType === "subSample" &&
      this.siblings.some((loc) => loc.selected)
    );
  }

  get allSiblingsSelected(): boolean {
    return this.siblings.every((loc) => loc.selected);
  }

  isSelectable(opts: {| onlyAllowSelectingEmptyLocations: boolean |}): boolean {
    if (opts.onlyAllowSelectingEmptyLocations) return !this.hasContent;
    return !this.isGreyedOut;
  }

  get name(): ?string {
    return this.content?.name;
  }

  get paramsForBackend(): {} {
    const params: {
      parentContainer: mixed,
      content: ?(SubSample | Container),
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
  toggleSelected(value: ?boolean): void {
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
        const firstNotPlaced:
          | ContainerModel
          // $FlowExpectedError[incompatible-type] this.search.results will be Array<ContainerModel | SubSampleModel>
          | SubSampleModel = moveStore.selectedResults.find(
          (r) => !selectedGlobalIds.includes(r.globalId)
        );

        // set new location
        this.content = firstNotPlaced;
      } else {
        this.content = null;
      }
    }

    this.selected = value;

    // Sync selection of location and content
    if (this.parentContainer.cType !== "LIST" && !moveStore.isMoving) {
      const activeContainer = getRootStore().searchStore.activeResult;
      // $FlowExpectedError[prop-missing]
      if (activeContainer?.contentSearch) {
        // $FlowExpectedError[incompatible-use] This is only used when activeResult is a container, with contentSearch
        const content = activeContainer.contentSearch.cacheFetcher.results.find(
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
