// @flow

import ContentsChips from "../../Inventory/Container/Content/ContentsChips";
import { type URL, type Point } from "../../util/types";
import { type _LINK } from "../../common/ApiServiceBase";
import { match, classMixin, clamp } from "../../util/Util";
import * as ArrayUtils from "../../util/ArrayUtils";
import { selectColor } from "../../util/colors";
import RsSet from "../../util/set";
import { layoutToLabels } from "../../util/table";
import {
  type Container,
  type ContainerType,
  type Location,
  type GridLayout,
  type ContentSummary,
  cTypeToDefaultSearchView,
} from "../definitions/Container";
import {
  type AllowedTypeFilters,
  type Search as SearchInterface,
  type CoreFetcherArgs,
} from "../definitions/Search";
import {
  type HasEditableFields,
  type HasUneditableFields,
} from "../definitions/Editable";
import {
  type InventoryRecord,
  type RecordType,
  type Action,
  type SharingMode,
  inventoryRecordTypeLabels,
} from "../definitions/InventoryRecord";
import { type Id, type GlobalId } from "../definitions/BaseRecord";
import { type RecordDetails } from "../definitions/Record";
import { type AdjustableTableRowOptions } from "../definitions/Tables";
import {
  type Permissioned,
  mapPermissioned,
} from "../definitions/PermissionedData";
import { type BlobUrl } from "../stores/ImageStore";
import getRootStore from "../stores/RootStore";
import { type AttachmentAttrs } from "./AttachmentModel";
import { type ExtraFieldAttrs } from "../definitions/ExtraField";
import LocationModel, { type LocationAttrs } from "./LocationModel";
import { Movable } from "./Movable";
import { type PersonAttrs } from "../definitions/Person";
import Result, {
  defaultVisibleResultFields,
  defaultEditableResultFields,
  RESULT_FIELDS,
  type ResultEditableFields,
  type ResultUneditableFields,
} from "./Result";
import { type Factory } from "../definitions/Factory";
import ResultCollection, {
  type ResultCollectionEditableFields,
} from "./ResultCollection";
import Search from "./Search";
import SubSampleModel, { type SubSampleAttrs } from "./SubSampleModel";
import {
  action,
  computed,
  observable,
  override,
  makeObservable,
  runInAction,
} from "mobx";
import React, { type Node } from "react";
import ListContainerIllustration from "../../assets/graphics/RecordTypeGraphics/HeaderIllustrations/ListContainer";
import GridContainerIllustration from "../../assets/graphics/RecordTypeGraphics/HeaderIllustrations/GridContainer";
import VisualContainerIllustration from "../../assets/graphics/RecordTypeGraphics/HeaderIllustrations/VisualContainer";
import { type BarcodeAttrs } from "../definitions/Barcode";
import { type SubSample } from "../definitions/SubSample";
import { type SharedWithGroup } from "../definitions/Group";
import type { IdentifierAttrs } from "../definitions/Identifier";
import { type Tag } from "../definitions/Tag";
import { pick } from "../../util/unsafeUtils";
import {
  IsInvalid,
  IsValid,
  type ValidationResult,
  allAreValid,
} from "../../components/ValidatingSubmitButton";
import * as Parsers from "../../util/parsers";

type ContainerEditableFields = {
  ...ResultEditableFields,
  ...
};

type ContainerUneditableFields = {
  ...ResultUneditableFields,
  location: InventoryRecord,
};

export type ContainerInContainerParams = {
  parentContainers?: Array<{ id: Id }>,
  parentLocation?: Location,
};

export type ContainerAttrs = {|
  id: ?Id,
  type: string,
  globalId: ?GlobalId,
  name: string,
  canStoreContainers: boolean,
  canStoreSamples: boolean,
  description: string,
  permittedActions: Array<Action>,
  quantity: null,
  extraFields?: Array<ExtraFieldAttrs>,
  tags: ?Array<Tag>,
  locations: $ReadOnlyArray<{
    ...$Diff<LocationAttrs, {| parentContainer: mixed, content: mixed |}>,
    content: ?(ContainerAttrs | SubSampleAttrs),
  }>,
  gridLayout: ?GridLayout,
  cType: string,
  locationsCount: ?number,
  contentSummary: ?ContentSummary,
  image?: string,
  parentContainers: Array<ContainerAttrs>,
  lastNonWorkbenchParent: ?ContainerAttrs,
  lastMoveDate: ?Date,
  owner: ?PersonAttrs,
  created: ?string,
  lastModified: ?string,
  modifiedByFullName: ?string,
  deleted: boolean,
  attachments: Array<AttachmentAttrs>,
  barcodes: Array<BarcodeAttrs>,
  identifiers: Array<IdentifierAttrs>,
  sharingMode: SharingMode,
  sharedWith: Array<SharedWithGroup>,
  _links: Array<_LINK>,
|};

const DEFAULT_CONTAINER = {
  id: null,
  type: "CONTAINER",
  globalId: null,
  name: "",
  canStoreContainers: true,
  canStoreSamples: true,
  description: "",
  permittedActions: ["READ", "UPDATE", "CHANGE_OWNER"],
  quantity: null,
  tags: null,
  locations: ([]: Array<{
    ...$Diff<LocationAttrs, {| parentContainer: mixed, content: mixed |}>,
    content: ?(ContainerAttrs | SubSampleAttrs),
  }>),
  gridLayout: null,
  cType: "LIST",
  locationsCount: Infinity,
  contentSummary: { totalCount: 0, subSampleCount: 0, containerCount: 0 },
  // user bench is added to parentConatiners for new Movable
  parentContainers: ([]: Array<ContainerAttrs>),
  lastNonWorkbenchParent: null,
  lastMoveDate: null,
  owner: null,
  created: null,
  deleted: false,
  lastModified: null,
  modifiedByFullName: null,
  attachments: ([]: Array<AttachmentAttrs>),
  barcodes: ([]: Array<BarcodeAttrs>),
  identifiers: ([]: Array<IdentifierAttrs>),
  sharingMode: "OWNER_GROUPS",
  sharedWith: ([]: Array<SharedWithGroup>),
  _links: ([]: Array<_LINK>),
};

const FIELDS = new Set([
  ...RESULT_FIELDS,
  "canStoreContainers",
  "canStoreSamples",
  "quantity",
  "locations",
  "organization",
  "locationsImage",
  "canStore",
]);
const defaultVisibleFields = new Set([
  ...FIELDS,
  ...defaultVisibleResultFields,
]);
const defaultEditableFields = new Set([
  ...defaultEditableResultFields,
  "organization",
  "canStore",
]);

export default class ContainerModel
  extends Result
  implements
    Container,
    HasEditableFields<ContainerEditableFields>,
    HasUneditableFields<ContainerUneditableFields>
{
  canStoreContainers: boolean = true;
  canStoreSamples: boolean = true;
  quantity: null = null; // Could this be removed from the API?
  locations: ?Array<Location> = [];
  unchangedLocationsIds: Array<number> = [];
  locationsImage: ?BlobUrl = null;
  gridLayout: ?GridLayout = null;
  newBase64LocationsImage: ?string = null;
  cType: ContainerType = "LIST";
  selectionMode: boolean = false;
  selectionStart: Point = { x: 0, y: 0 };
  selectionEnd: Point = { x: 0, y: 0 };
  initializedLocations: boolean = false;
  locationsCount: number;
  contentSummary: Permissioned<ContentSummary>;
  contentSearch: SearchInterface;
  parentContainers: Array<Container>;
  parentLocation: ?Location;
  allParentContainers: ?() => Array<Container>;
  rootParentContainer: ?Container;
  immediateParentContainer: ?Container;
  lastNonWorkbenchParent: ?Container;
  lastMoveDate: ?Date;
  siblingColorCache: Map<Id, string> = new Map<Id, string>();

  constructor(factory: Factory, params: ContainerAttrs = DEFAULT_CONTAINER) {
    super(factory);
    makeObservable(this, {
      canStoreContainers: observable,
      canStoreSamples: observable,
      quantity: observable,
      locations: observable,
      unchangedLocationsIds: observable,
      locationsImage: observable,
      gridLayout: observable,
      newBase64LocationsImage: observable,
      cType: observable,
      selectionMode: observable,
      selectionStart: observable,
      selectionEnd: observable,
      initializedLocations: observable,
      locationsCount: observable,
      contentSummary: observable,
      contentSearch: observable,
      parentLocation: observable,
      parentContainers: observable,
      allParentContainers: observable,
      rootParentContainer: observable,
      immediateParentContainer: observable,
      lastNonWorkbenchParent: observable,
      lastMoveDate: observable,
      updateLocationsCount: action,
      setOrganization: action,
      findLocation: action,
      startSelection: action,
      moveSelection: action,
      stopSelection: action,
      onSelect: action,
      deleteSortedLocation: action,
      toggleAllLocations: action,
      updateFieldsState: override,
      fetchAdditionalInfo: override,
      paramsForBackend: override,
      recordTypeLabel: override,
      cardTypeLabel: override,
      iconName: override,
      recordType: override,
      showNewlyCreatedRecordSearchParams: override,
      populateFromJson: override,
      children: override,
      recordDetails: override,
      supportsBatchEditing: override,
      permalinkURL: override,
      fieldNamesInUse: override,
      allowedTypeFilters: computed,
      contentCount: computed,
      hasEnoughSpace: computed,
      isFull: computed,
      results: computed,
      canStoreRecordTypes: computed,
      movingIntoItself: computed,
      canStoreRecords: computed,
      canStoreRecordsFromInfoData: computed,
      availableLocations: computed,
      canStore: computed,
      dimensions: computed,
      selectedLocations: computed,
      selectedResults: computed,
      shallowSelected: computed,
      shallowUnselected: computed,
      rows: computed,
      columns: computed,
      sortedLocations: computed,
      getLocationsForApi: computed,
      siblingGroups: computed,
      containerTypeLabel: computed,
      hasSelectedLocation: computed,
      hasSelectedRecord: computed,
    });

    if (this.recordType === "container")
      this.populateFromJson(factory, params, DEFAULT_CONTAINER);
  }

  populateFromJson(factory: Factory, params: any, defaultParams: ?any = {}) {
    super.populateFromJson(factory, params, defaultParams);
    params = { ...DEFAULT_CONTAINER, ...params };
    this.canStoreContainers = params.canStoreContainers;
    this.canStoreSamples = params.canStoreSamples;
    this.quantity = params.quantity;
    this.locationsImage = null;
    this.gridLayout = params.gridLayout;
    this.cType = params.cType;
    this.locationsCount =
      params.locationsCount === null ? Infinity : params.locationsCount;
    this.contentSummary = params.contentSummary
      ? {
          isAccessible: true,
          value: params.contentSummary,
        }
      : {
          isAccessible: false,
        };
    this.parentLocation = params.parentLocation;
    this.parentContainers = params.parentContainers;
    this.lastNonWorkbenchParent = params.lastNonWorkbenchParent;
    this.lastMoveDate = params.lastMoveDate;
    // $FlowFixMe[prop-missing] Defined on the mixin
    this.initializeMovableMixin(factory);

    const searchParams = {
      fetcherParams: {
        parentGlobalId: this.globalId,
      },
      uiConfig: {
        allowedSearchModules: new Set(["TYPE", "OWNER", "TAG"]),
        allowedTypeFilters: this.allowedTypeFilters,
        hideContentsOfChip: true,
      },
      factory: factory.newFactory(),
    };
    if (!this.contentSearch) this.contentSearch = new Search(searchParams);
    void this.contentSearch.setSearchView(cTypeToDefaultSearchView(this.cType));

    const locations: Array<Location> = (params.locations ?? []).map((l) => {
      const content = l.content ? factory.newRecord(l.content) : null;
      content?.populateFromJson(factory, l.content);
      return new LocationModel({
        ...l,
        content,
        parentContainer: this,
      });
    });
    this.contentSearch.cacheFetcher.setResults(
      locations.map((l) => l.content).filter(Boolean)
    );
    this.initializedLocations = false;
    if (this.cType === "LIST") this.locations = locations;
    if (this.cType === "IMAGE") this.locations = locations;
    if (this.cType === "GRID")
      this.locations = ArrayUtils.outerProduct<number, number, Location>(
        this.rows.map((r) => r.value),
        this.columns.map((c) => c.value),
        (coordY: number, coordX: number): Location =>
          locations.find((l) => l.coordX === coordX && l.coordY === coordY) ??
          new LocationModel({
            id: null,
            coordX,
            coordY,
            content: null,
            parentContainer: this,
          })
      ).flat();
    if (this.locations) {
      this.unchangedLocationsIds = Object.freeze(
        // $FlowExpectedError[incompatible-call]
        this.locations.map((l) => l.id)
      );
      this.initializedLocations = true;
    }
  }

  get allowedTypeFilters(): AllowedTypeFilters {
    const set: AllowedTypeFilters = new Set();
    if (this.canStoreContainers) set.add("CONTAINER");
    if (this.canStoreSamples) set.add("SUBSAMPLE");
    if (set.size > 1) set.add("ALL");
    // set will be empty in public view case
    return set;
  }

  get recordType(): RecordType {
    return "container";
  }

  get contentCount(): Permissioned<number> {
    return mapPermissioned(this.contentSummary, ({ totalCount }) => totalCount);
  }

  get hasEnoughSpace(): ?boolean {
    if (this.cType === "LIST") return true;
    if (this.cType === "WORKBENCH") return true;
    const locations = this.locations;
    if (!locations) throw new Error("Locations of container must be known.");

    const selectedResults = new RsSet(getRootStore().moveStore.selectedResults);
    const gIdsOfItemsBeingMoved = selectedResults.map((rec) => rec.globalId);
    const gIdsOfContentOfLocations = new RsSet(this.locations).map(
      (l) => l.content?.globalId
    );
    const gIdsOfItemsBeingMovedThatAreYetToBePlaced =
      gIdsOfItemsBeingMoved.subtract(gIdsOfContentOfLocations);
    const numberOfEmptySlots = locations.filter((loc) => !loc.content).length;
    return numberOfEmptySlots >= gIdsOfItemsBeingMovedThatAreYetToBePlaced.size;
  }

  get isFull(): ?boolean {
    if (!this.availableLocations.isAccessible) return null;
    return Number(this.availableLocations.value) === 0;
  }

  get results(): Array<Container | SubSample> {
    return this.contentSearch.filteredResults;
  }

  get canStoreRecordTypes(): boolean {
    const moveStore = getRootStore().moveStore;
    return (
      (!moveStore.selectedResultsIncludesContainers ||
        this.canStoreContainers) &&
      (!moveStore.selectedResultsIncludesSubSamples || this.canStoreSamples)
    );
  }

  /*
   * Checks if any of the selected records for moving include either this
   * container or one of its parents to prevent cycles
   */
  get movingIntoItself(): boolean {
    if (!this.allParentContainers)
      throw new Error("Not yet fully initialised.");
    const allParentContainers = this.allParentContainers();
    const moveStore = getRootStore().moveStore;
    const selectedIds = new RsSet(
      moveStore.selectedResults.map((r) => r.globalId)
    );
    const parentAndThisIds = new RsSet([
      ...allParentContainers.map((c) => c.globalId),
      this.globalId,
    ]);
    return !selectedIds.intersection(parentAndThisIds).isEmpty;
  }

  /* Calculates whether moveStore can perform an operation that would store its current selection
   *  in this container. This should be used where this.infoLoaded is true, such as submission buttons.
   */
  get canStoreRecords(): boolean {
    return this.canStoreRecordsFromInfoData && Boolean(this.hasEnoughSpace);
  }

  /* Just as canStoreRecords above, this determines whether moveStore's current selection can be moved
   *  into this container, but with only the information known before this.infoLoaded is true. We cannot
   *  fully determine whether there is enough space as we don't know what the overlap between
   *  moveStore.selectedResults and this.results is. This should be used where fetchAdditionalInfo has
   *  not yet been performed, like SelectTargetContainer.
   */
  get canStoreRecordsFromInfoData(): boolean {
    return this.canStoreRecordTypes && !this.movingIntoItself && !this.deleted;
  }

  get availableLocations(): Permissioned<number> {
    return mapPermissioned(
      this.contentCount,
      (contentCount) => this.locationsCount - contentCount
    );
  }

  get canStore(): Array<"containers" | "samples"> {
    return [
      ...(this.canStoreContainers ? ["containers"] : []),
      ...(this.canStoreSamples ? ["samples"] : []),
    ];
  }

  get dimensions(): ?[number | "", number | ""] {
    return this.gridLayout
      ? [this.gridLayout.columnsNumber, this.gridLayout.rowsNumber]
      : null;
  }

  get selectedLocations(): ?Array<Location> {
    return this.locations?.filter((l: Location) => l.selected);
  }

  get selectedResults(): Array<Container | SubSample> {
    return this.results.filter((c) => c.selected);
  }

  get shallowSelected(): Array<Location> {
    const locations = this.locations;
    if (!locations) throw new Error("Locations of container must be known.");
    return locations.filter((l: Location) => l.isShallowSelected);
  }

  get shallowUnselected(): Array<Location> {
    const locations = this.locations;
    if (!locations) throw new Error("Locations of container must be known.");
    return locations.filter((l: Location) => l.isShallowUnselected);
  }

  get rows(): Array<{ value: number, label: string | number }> {
    if (!this.gridLayout) return [];
    if (this.gridLayout.rowsNumber === "") return [];
    return layoutToLabels(
      this.gridLayout.rowsLabelType,
      this.gridLayout.rowsNumber
    );
  }

  get columns(): Array<{
    value: number,
    label: string | number,
  }> {
    if (!this.gridLayout) return [];
    if (this.gridLayout.columnsNumber === "") return [];
    return layoutToLabels(
      this.gridLayout.columnsLabelType,
      this.gridLayout.columnsNumber
    );
  }

  get sortedLocations(): ?Array<Location> {
    const locations = this.locations;
    if (!locations) return null;
    const existingLocations = locations.filter(
      (l) => Boolean(l.id) && l instanceof LocationModel
    );
    const newLocations = locations.filter((l) => !l.id);
    return [
      ...existingLocations.sort((a, b) => (a.id ?? 0) - (b.id ?? 0)),
      ...newLocations,
    ];
  }

  get getLocationsForApi(): Array<
    | {|
        id: ?number,
        coordX: ?number,
        coordY: ?number,
        newLocationRequest?: boolean,
      |}
    | {|
        id: number,
        deleteLocationRequest: boolean,
      |}> {
    const locationModelToObject = pick("id", "coordX", "coordY");

    const locations = this.locations;
    if (!locations) throw new Error("Locations of container must be known.");

    const newLocations = locations.filter((l) => !l.id);
    const existingLocations = locations.filter((l) => Boolean(l.id));

    const existingLocationIds = existingLocations.map((l) => l.id);
    const deletedLocationIds = this.unchangedLocationsIds.filter(
      (id) => !existingLocationIds.includes(id)
    );

    return [
      ...existingLocations.map(locationModelToObject),
      ...newLocations.map((l) => ({
        newLocationRequest: true,
        ...locationModelToObject(l),
      })),
      ...deletedLocationIds.map((id) => ({
        deleteLocationRequest: true,
        id,
      })),
    ];
  }

  get siblingGroups(): RsSet<Id> {
    const locations = this.locations;
    if (!locations) throw new Error("Locations of container must be known.");
    return new RsSet(locations)
      .map(({ content }) => content)
      .filterClass(SubSampleModel)
      .map(({ sample: { id } }) => id);
  }

  getColor(sampleId: Id): ?string {
    if (this.siblingColorCache.has(sampleId)) {
      return this.siblingColorCache.get(sampleId);
    }
    const groups = this.siblingGroups;
    // recalculate all colours so that they're evenly spaced through the hues
    this.siblingColorCache = new Map(
      groups
        .toArray()
        .map((siblingSampleId, i) => [
          siblingSampleId,
          selectColor(i, groups.size),
        ])
    );
    return this.siblingColorCache.get(sampleId);
  }

  updateLocationsCount(delta: number) {
    this.locationsCount = this.locationsCount + delta;
  }

  setOrganization(organization: ContainerType) {
    this.cType = organization;

    switch (organization) {
      case "GRID":
        this.gridLayout = {
          columnsNumber: 1,
          rowsNumber: 1,
          rowsLabelType: "ABC",
          columnsLabelType: "N123",
        };
        break;
      case "IMAGE":
        this.gridLayout = null;
        if (this.image && this.newBase64Image) {
          this.setAttributes({
            newBase64LocationsImage: this.newBase64Image,
            locationsImage: this.image,
          });
        }
        break;
      case "LIST":
        this.gridLayout = null;
    }
  }

  updateFieldsState() {
    this.currentlyVisibleFields = defaultVisibleFields;
    this.currentlyEditableFields = defaultEditableFields;
    switch (this.state) {
      case "edit":
        this.setEditable(FIELDS, true);
        this.setVisible(new Set(["organization"]), false);
        this.setEditable(new Set(["organization", "locations"]), false);
        this.setEditableExtraFields(this.extraFields, true);
        break;
      case "preview":
        this.setEditable(FIELDS, false);
        this.setVisible(new Set(["organization"]), false);
        break;
      case "create":
        this.setEditable(FIELDS, true);
    }
  }

  /*
   * When batch editing, we want all fields to begin in a disabled state, with
   * the user choosing to enable the fields that they wish to edit. By default,
   * when a record is in edit mode, most of the fields are enabled, however.
   * Therefore, this method provides a simple way to set all of the fields back
   * to not being editable after batch editing has been enabled.
   */
  setFieldsStateForBatchEditing() {
    this.setEditable(FIELDS, false);
  }

  async fetchAdditionalInfo(
    silent: boolean = false,
    queryParameters: URLSearchParams = new URLSearchParams({
      includeContent: "true",
    })
  ) {
    if (this.isWorkbench) {
      this.setLoading(false);
      return;
    }
    if (this.fetchingAdditionalInfo) {
      await this.fetchingAdditionalInfo;
      return;
    }
    runInAction(() => {
      this.initializedLocations = false;
    });
    this.fetchingAdditionalInfo = super.fetchAdditionalInfo(
      silent,
      queryParameters
    );
    await this.fetchingAdditionalInfo;
    this.setLoading(true);

    if (this.cType === "IMAGE") {
      runInAction(() => {
        this.locationsImage = null;
      });
      await this.fetchImage("locationsImage");
    }

    this.setLoading(false);
  }

  findLocation(col: number, row: number): ?Location {
    return this.locations?.find(
      (loc) => loc.coordX === col && loc.coordY === row
    );
  }

  toggleAllLocations(value: boolean) {
    this.selectedLocations?.forEach((l) => l.toggleSelected(value));
  }

  /*
   * A plain object that can be encoded to JSON for submission to the backend
   * when API calls are made. It is vital that there are no cyclical memory
   * references in the object returned by this computed properties. See
   * ./__tests__/ContainerModel/paramsForBackend.test.js for the tests that assert
   * that this object can be serialised; any changes should be reflected there.
   */
  get paramsForBackend(): any {
    const params = { ...super.paramsForBackend };
    if (this.currentlyEditableFields.has("canStoreContainers"))
      params.canStoreContainers = this.canStoreContainers;
    if (this.currentlyEditableFields.has("canStoreSamples"))
      params.canStoreSamples = this.canStoreSamples;
    if (this.cType === "IMAGE") params.locations = this.getLocationsForApi;
    if (this.currentlyEditableFields.has("locationsImage"))
      params.newBase64LocationsImage = this.newBase64LocationsImage;
    if (this.currentlyEditableFields.has("organization"))
      params.cType = this.cType;
    params.gridLayout = this.gridLayout;
    return params;
  }

  startSelection(
    event: MouseEvent,
    padding: {|
      left: number,
      top: number,
      right: number,
      bottom: number,
    |} = { left: 0, top: 0, right: 0, bottom: 0 }
  ) {
    if (event.button === 2) return;
    if (event.currentTarget instanceof Element) {
      const currentTarget: Element = event.currentTarget;

      this.selectionMode = true;
      const currentTargetRect = event.currentTarget.getBoundingClientRect();
      this.selectionStart = {
        x: event.pageX + currentTarget.scrollLeft - currentTargetRect.left,
        y: event.pageY - (currentTargetRect.top + window.scrollY),
      };
      this.moveSelection(event, padding);
    }
  }

  moveSelection(
    event: MouseEvent,
    padding: {|
      left: number,
      top: number,
      right: number,
      bottom: number,
    |} = { left: 0, top: 0, right: 0, bottom: 0 }
  ) {
    // currentTarget is either Table or TableContainer
    if (event.currentTarget instanceof Element) {
      const currentTarget: Element = event.currentTarget;
      const currentTargetRect = currentTarget.getBoundingClientRect();
      const horzScroll = currentTarget.scrollLeft;
      const vertScroll = window.scrollY;
      const widthOfSelectableArea = currentTarget.clientWidth;
      const heightOfSelectableArea = currentTarget.clientHeight;

      // These are relative to viewport
      const leftPosOfSelectableArea = currentTargetRect.left;
      const topPosOfSelectableArea = currentTargetRect.top;

      const DRAGGER_SIZE = 24;
      /*
       * The reason why x is pageX + horzScroll - leftPos and y
       * is pageY - vertScroll - topPos is because horizontal
       * scrolling is scoped to the table whereas vertical scrolling
       * is scoped to the whole page/dialog
       */
      this.selectionEnd = {
        x: clamp(
          event.pageX + horzScroll - leftPosOfSelectableArea,
          padding.left + horzScroll,
          widthOfSelectableArea +
            horzScroll -
            (DRAGGER_SIZE / 2 + padding.right)
        ),
        y: clamp(
          event.pageY - vertScroll - topPosOfSelectableArea,
          padding.top,
          heightOfSelectableArea - (DRAGGER_SIZE / 2 + padding.bottom)
        ),
      };
    }
  }

  stopSelection(): void {
    const locations = this.locations;
    if (!locations) throw new Error("Locations of container must be known.");
    locations
      .filter((loc) => loc.isShallow && loc.isSelectable)
      .map((loc) => this.onSelect(loc));
    this.selectionMode = false;
  }

  onSelect(location: Location): void {
    if (!this.selectedLocations)
      throw new Error("Locations of container must be known.");
    const selectedLocations = this.selectedLocations;

    if (location.selected) {
      location.toggleSelected(false);
    } else {
      const restrainedSelect = getRootStore().moveStore.isMoving;
      const canSelectOneMore =
        selectedLocations.length + 1 <=
        getRootStore().moveStore.selectedResults.length;
      const canSelectThisLocation = location.isSelectable;

      if (restrainedSelect) {
        if (canSelectOneMore && canSelectThisLocation) {
          location.toggleSelected(true);
        }
      } else {
        location.toggleSelected(Boolean(location.content));
      }
    }
  }

  // have to delete by index of sortedLocations because new locations don't yet have an id
  deleteSortedLocation(index: number): void {
    if (!this.sortedLocations)
      throw new Error("Locations of container must be known.");
    const sortedLocations = this.sortedLocations;
    sortedLocations.splice(index, 1);
    this.updateLocationsCount(-1);
    this.setAttributesDirty({
      locations: sortedLocations,
    });
  }

  get recordTypeLabel(): string {
    return this.cType === "WORKBENCH"
      ? inventoryRecordTypeLabels.bench
      : inventoryRecordTypeLabels.container;
  }

  get containerTypeLabel(): string {
    return match<ContainerType, string>([
      [(t) => t === "IMAGE", "Visual"],
      [(t) => t === "LIST", "List"],
      [(t) => t === "GRID", "Grid"],
      [(t) => t === "WORKBENCH", "Bench"],
    ])(this.cType);
  }

  get cardTypeLabel(): string {
    if (this.cType === "WORKBENCH") {
      return this.containerTypeLabel;
    }
    if (this.cType) {
      return `${this.containerTypeLabel} ${this.recordTypeLabel}`;
    }
    return this.recordTypeLabel;
  }

  get hasSelectedLocation(): boolean {
    if (!this.selectedLocations)
      throw new Error("Locations of container must be known.");
    const selectedLocations = this.selectedLocations;
    return Boolean(selectedLocations[0]);
  }

  get hasSelectedRecord(): boolean {
    return Boolean(this.selectedResults[0]);
  }

  contextMenuDisabled(): ?string {
    const listViewAndASelectedRecord =
      this.contentSearch.searchView === "LIST" && this.hasSelectedRecord;
    const gridOrImageViewAndSelectedLocation =
      (this.contentSearch.searchView === "GRID" ||
        this.contentSearch.searchView === "IMAGE") &&
      this.hasSelectedLocation;
    return (
      super.contextMenuDisabled() ??
      (listViewAndASelectedRecord || gridOrImageViewAndSelectedLocation
        ? "Cannot modify this container whilst its contents are selected."
        : null)
    );
  }

  get fieldNamesInUse(): Array<string> {
    return [
      ...super.fieldNamesInUse,
      ...["Can Store", "Type", "Locations Image", "Grid Dimensions"],
    ];
  }

  adjustableTableOptions(): AdjustableTableRowOptions<string> {
    const renderAvailableLocations = (): ?string => {
      if (!this.availableLocations.isAccessible) return null;
      if (isFinite(this.availableLocations.value))
        return `${this.availableLocations.value}`;
      return "Unlimited";
    };

    const options = new Map([
      ...super.adjustableTableOptions(),
      // $FlowFixMe[prop-missing] Defined on the mixin
      ...this.adjustableTableOptions_movable(),
    ]);
    if (this.readAccessLevel !== "full") {
      options.set("Contents", () => ({ renderOption: "node", data: null }));
    } else {
      options.set("Contents", () => ({
        renderOption: "node",
        data: <ContentsChips record={this} />,
      }));
    }
    options.set("Number of Empty Locations", () => ({
      renderOption: "node",
      data: renderAvailableLocations(),
    }));
    if (this.readAccessLevel === "public") {
      options.set("Container Type", () => ({
        renderOption: "node",
        data: null,
      }));
    } else {
      options.set("Container Type", () => ({
        renderOption: "node",
        data: this.containerTypeLabel,
      }));
    }
    return options;
  }

  get iconName(): string {
    return this.isWorkbench ? "bench" : "container";
  }

  get showNewlyCreatedRecordSearchParams(): CoreFetcherArgs {
    // when specifically creating a container inside another
    if (this.parentContainers) {
      return {
        parentGlobalId: this.parentContainers[0].globalId,
      };
    }

    // otherwise new containers get added to the bench
    const currentUser = getRootStore().peopleStore.currentUser;
    if (!currentUser) throw new Error("Current user is not known.");
    return {
      parentGlobalId: `BE${currentUser.workbenchId}`,
    };
  }

  get illustration(): Node {
    if (this.cType === "IMAGE") return <VisualContainerIllustration />;
    if (this.cType === "GRID") return <GridContainerIllustration />;
    return <ListContainerIllustration />;
  }

  validate(): ValidationResult {
    const validateCanStore = () => {
      if (this.canStoreContainers || this.canStoreSamples) return IsValid();
      return IsInvalid(
        "Must be permitted to contain either containers or subsamples."
      );
    };

    const validateGridLayout = () => {
      if (!this.gridLayout) return IsValid();
      const { columnsNumber, rowsNumber } = this.gridLayout;
      /*
       * The error messages reported here are not as helpful as they could be,
       * but there is little we can with compromising the user experience in
       * other ways. When the GridDimensions react component renders two
       * NumberFields, it does so with `min` and `max` properties so that the
       * browser may limit the values that can be entered with the arrow keys.
       * `checkValidity` is then used to prevent values outside of the allowed
       * range from being set in the `.gridLayout` property of instances of
       * this class. As such, when the columnsNumber and rowsNumber as set from
       * the UI, the will either have an integer value between 1 and 24 or an
       * empty string; limiting us to only being able to say that the field is
       * invalid here. Nonetheless, we still must check the range of values in
       * case some other part of the code is setting these to out-of-range
       * values.
       */
      return allAreValid([
        Parsers.isNumber(columnsNumber)
          .mapError(() => new Error("Number of columns is invalid."))
          .flatMap((cols) => {
            if (cols <= 0)
              return IsInvalid("Number of columns must be a positive value.");
            if (cols > 24)
              return IsInvalid("Number of columns cannot be greater than 24.");
            return IsValid();
          }),
        Parsers.isNumber(rowsNumber)
          .mapError(() => new Error("Number of rows is invalid."))
          .flatMap((rows) => {
            if (rows <= 0)
              return IsInvalid("Number of rows must be a positive value.");
            if (rows > 24)
              return IsInvalid("Number of rows cannot be greater than 24.");
            return IsValid();
          }),
      ]);
    };

    return allAreValid([
      super.validate(),
      validateCanStore(),
      validateGridLayout(),
    ]);
  }

  get children(): Array<InventoryRecord> {
    return this.contentSearch.cacheFetcher.results;
  }

  loadChildren(): void {
    void this.fetchAdditionalInfo();
  }

  get canNavigateToChildren(): boolean {
    return true;
  }

  get recordDetails(): RecordDetails {
    return Object.assign(
      { ...super.recordDetails },
      {
        contents: this,
        location: this,
      }
    );
  }

  /*
   * The current value of the editable fields, as required by the interface
   * `HasEditableFields` and `HasUneditableFields`.
   */
  get fieldValues(): ContainerEditableFields & ContainerUneditableFields {
    return {
      ...super.fieldValues,
      location: this,
    };
  }

  get supportsBatchEditing(): boolean {
    return true;
  }

  //eslint-disable-next-line no-unused-vars
  get noValueLabel(): {[key in keyof ContainerEditableFields]: ?string} & {[key in keyof ContainerUneditableFields]: ?string} {
    return {
      ...super.noValueLabel,
      location: null,
    };
  }

  get permalinkURL(): ?URL {
    if (!this.globalId) return null;
    if (this.isWorkbench)
      return `/inventory/search?parentGlobalId=${this.globalId}`;
    return super.permalinkURL;
  }

  refreshAssociatedSearch() {
    if (this.id !== null) {
      void this.contentSearch.setSearchView(
        cTypeToDefaultSearchView(this.cType)
      );
      void this.contentSearch.fetcher.performInitialSearch();
    }
  }

  showTopLinkInBreadcrumbs(): boolean {
    return !this.isWorkbench && !this.isInWorkbench();
  }

  get usableInLoM(): boolean {
    return true;
  }

  get beingCreatedInContainer(): boolean {
    return !this.isOnWorkbench();
  }

  get inContainerParams(): ContainerInContainerParams {
    return {
      ...((this.beingCreatedInContainer
        ? { parentContainers: [{ id: this.parentContainers[0].id }] }
        : {}): { parentContainers?: Array<{ id: Id }> }),
      ...(this.parentLocation ? { parentLocation: this.parentLocation } : {}),
    };
  }

  get dataAttachedToRecordCreatedAnaylticsEvent(): {} {
    return {
      ...super.dataAttachedToRecordCreatedAnaylticsEvent,
      cType: this.cType,
    };
  }
}

classMixin(ContainerModel, Movable);

type BatchContainerEditableFields = {
  ...ResultCollectionEditableFields,
  ...
};

/*
 * This is a wrapper class around a set of Containers, making it easier to
 * perform batch operations e.g. editing.
 */
export class ContainerCollection
  extends ResultCollection<ContainerModel>
  implements HasEditableFields<BatchContainerEditableFields>
{
  constructor(containers: RsSet<ContainerModel>) {
    super(containers);
    makeObservable(this, {
      setFieldsDirty: override,
      fieldValues: override,
    });
  }

  get fieldValues(): BatchContainerEditableFields {
    return super.fieldValues;
  }

  //eslint-disable-next-line no-unused-vars
  get noValueLabel(): {[key in keyof BatchContainerEditableFields]: ?string} {
    return super.noValueLabel;
  }

  setFieldsDirty(newFieldValues: any): void {
    super.setFieldsDirty(newFieldValues);
  }

  setFieldEditable(fieldName: string, value: boolean): void {
    for (const container of this.records) {
      container.setFieldEditable(fieldName, value);
    }
  }
}
