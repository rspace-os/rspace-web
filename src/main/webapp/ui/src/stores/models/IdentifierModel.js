//@flow

/*
 * ====  A POINT ABOUT THE IMPORTS  ===========================================
 *
 *  This class is used, amongst other places, on the IdentifierPublicPage[1]
 *  where the user may not be authenticated. As such, this module, and any
 *  module that is imported, MUST NOT import anything from the global Inventory
 *  stores (i.e. from ../stores/*). If it does, then the page will be rendered
 *  as a blank screen and there will be an unhelpful error message on the
 *  browser's console saying that webpack export could not be initialised. To
 *  avoid this, dependency injection is used to pass references that have been
 *  created in code that can depend on the global stores.
 *
 *  [1]: ../../components/PublicPage/IdentifierPublicPage.js
 *
 * ============================================================================
 */
import {
  observable,
  action,
  computed,
  makeObservable,
  runInAction,
} from "mobx";
import React, { type Node } from "react";
import GeoLocationModel from "../models/GeoLocationModel";
import { type Id, type GlobalId } from "../definitions/BaseRecord";
import { type URL } from "../../util/types";
import { type _LINK } from "../../common/ApiServiceBase";
import {
  type Identifier,
  type IdentifierAttrs,
  type AlternateIdentifier,
  type IdentifierDescription,
  type IdentifierSubject,
  type IdentifierField,
  type DropdownOption,
  type CreatorType,
  type IGSNPublishingState,
  type IdentifierDate,
} from "../definitions/Identifier";
import type { RadioOption } from "../../components/Inputs/RadioField";
import {
  type GeoLocation,
  type GeoLocationPolygon,
} from "../definitions/GeoLocation";
import { mkAlert, type Alert } from "../contexts/Alert";
import * as ArrayUtils from "../../util/ArrayUtils";
import typeof InvApiService from "../../common/InvApiService";

type GeoLocationBox = {
  eastBoundLongitude: string,
  northBoundLatitude: string,
  southBoundLatitude: string,
  westBoundLongitude: string,
};
type PolygonPoint = {
  pointLatitude: string,
  pointLongitude: string,
};

export type IdentifierGeoLocation = {
  geoLocationBox: GeoLocationBox,
  geoLocationPlace: string,
  geoLocationPoint: PolygonPoint,
  geoLocationPolygon: GeoLocationPolygon,
};

export const newGeoLocation = {
  geoLocationBox: {
    eastBoundLongitude: "",
    northBoundLatitude: "",
    southBoundLatitude: "",
    westBoundLongitude: "",
  },
  geoLocationPlace: "",
  geoLocationPoint: {
    pointLatitude: "",
    pointLongitude: "",
  },
  geoLocationPolygon: [
    {
      polygonPoint: {
        pointLatitude: "",
        pointLongitude: "",
      },
    },
    {
      polygonPoint: {
        pointLatitude: "",
        pointLongitude: "",
      },
    },
    {
      polygonPoint: {
        pointLatitude: "",
        pointLongitude: "",
      },
    },
    {
      polygonPoint: {
        pointLatitude: "",
        pointLongitude: "",
      },
    },
  ],
};

const creatorTypeOptions: Array<RadioOption<string>> = [
  {
    value: "Personal",
    label: "Personal",
  },
  {
    value: "Organizational",
    label: "Organizational",
  },
];

const identifierDescriptionOptions: Array<DropdownOption> = [
  { value: "ABSTRACT", label: "Abstract" },
  { value: "METHODS", label: "Methods" },
];
const identifierDateOptions: Array<DropdownOption> = [
  { value: "ACCEPTED", label: "Accepted" },
  { value: "AVAILABLE", label: "Available" },
  { value: "COPYRIGHTED", label: "Copyrighted" },
  { value: "COLLECTED", label: "Collected" },
  { value: "CREATED", label: "Created" },
  { value: "ISSUED", label: "Issued" },
  { value: "SUBMITTED", label: "Submitted" },
  { value: "UPDATED", label: "Updated" },
  { value: "VALID", label: "Valid" },
  { value: "WITHDRAWN", label: "Withdrawn" },
  { value: "OTHER", label: "Other" },
];

/**
 * subFields is a mapping function,
 * useful to help rendering in the MultipleInputHandler component.
 * more fields could be added in the future,
 * and their schema is defined by datacite, not RS.
 * this function lets us extract all properties different from "value" and "type".
 */
export const subFields = <Key: string, Value, Field: { [Key]: Value }>(
  field: Field
): Array<{| key: Key, value: Value |}> =>
  Object.entries(field)
    .filter((item) => item[0] !== "value" && item[0] !== "type")
    .map((item) => {
      return { key: item[0], value: item[1] };
    });

export const subFieldsForNew: { ... } = {
  Subjects: {
    subjectScheme: "",
    schemeURI: "",
    valueURI: "",
    classificationCode: "",
  },
  "Alternate Identifiers": {
    freeType: "",
  },
};

export const RECOMMENDED_FIELDS_LABELS = {
  type: "Type",
  freeType: "Type",
  subjectScheme: "Subject Scheme",
  schemeURI: "Scheme URI",
  valueURI: "Value URI",
  classificationCode: "Classification Code",
};

export default class IdentifierModel implements Identifier {
  parentGlobalId: GlobalId;
  id: Id;
  rsPublicId: ?string;
  doi: string;
  doiType: string;
  creatorName: string;
  creatorType: CreatorType;
  creatorAffiliation: ?string;
  creatorAffiliationIdentifier: ?string;
  title: string; // item.name
  publicUrl: ?URL;
  publisher: string;
  publicationYear: string;
  resourceType: string;
  resourceTypeGeneral: string;
  url: ?URL;
  state: IGSNPublishingState = "draft";
  subjects: ?Array<IdentifierSubject> = [];
  descriptions: ?Array<IdentifierDescription> = [];
  alternateIdentifiers: ?Array<AlternateIdentifier> = [];
  dates: ?Array<IdentifierDate> = [];
  geoLocations: ?$ReadOnlyArray<GeoLocation> = [];
  _links: Array<_LINK> = [];
  editing: boolean = false;
  customFieldsOnPublicPage: boolean;

  ApiServiceBase: InvApiService | null = null;

  constructor(
    attrs: IdentifierAttrs,
    parentGlobalId: GlobalId,
    ApiServiceBase?: InvApiService
  ) {
    makeObservable(this, {
      parentGlobalId: observable,
      id: observable,
      rsPublicId: observable,
      doi: observable,
      doiType: observable,
      creatorName: observable,
      creatorType: observable,
      creatorAffiliation: observable,
      creatorAffiliationIdentifier: observable,
      title: observable,
      publicUrl: observable,
      publisher: observable,
      publicationYear: observable,
      resourceType: observable,
      resourceTypeGeneral: observable,
      url: observable,
      state: observable,
      subjects: observable,
      descriptions: observable,
      alternateIdentifiers: observable,
      dates: observable,
      geoLocations: observable,
      _links: observable,
      editing: observable,
      customFieldsOnPublicPage: observable,
      requiredFields: computed,
      isValid: computed,
      recommendedFields: computed,
      anyRecommendedGiven: computed,
      requiredCompleted: computed,
      optionalCompleted: computed,
      doiTypeLabel: computed,
      setEditing: action,
      setCreatorName: action,
      setCreatorType: action,
      setTitle: action,
      setPublisher: action,
      setPublicationYear: action,
      setResourceType: action,
      setSubjects: action,
      setDescriptions: action,
      setAlternateIdentifiers: action,
      setDates: action,
      setGeoLocations: action,
      updateState: action,
      publish: action,
      retract: action,
    });

    this.parentGlobalId = parentGlobalId;
    this.id = attrs.id;
    this.rsPublicId = attrs.rsPublicId;
    this.doi = attrs.doi;
    this.doiType = attrs.doiType;
    this.creatorName = attrs.creatorName;
    this.creatorType = attrs.creatorType;
    this.creatorAffiliation = attrs.creatorAffiliation;
    this.creatorAffiliationIdentifier = attrs.creatorAffiliationIdentifier;
    this.title = attrs.title;
    this.publicUrl = attrs.publicUrl;
    this.publisher = attrs.publisher;
    this.publicationYear = `${attrs.publicationYear}`;
    this.resourceType = attrs.resourceType;
    this.resourceTypeGeneral = attrs.resourceTypeGeneral;
    this.url = attrs.url;
    this.state = attrs.state;
    /* falling back to empty array as backend may return null */
    this.subjects = attrs.subjects ?? [];
    this.descriptions = attrs.descriptions ?? [];
    this.alternateIdentifiers = attrs.alternateIdentifiers ?? [];
    this.dates =
      attrs.dates?.map((d) => {
        return { value: new Date(d.value), type: d.type };
      }) ?? [];
    this.geoLocations =
      attrs.geoLocations?.map((gl) => new GeoLocationModel(gl)) ?? [];
    this._links = attrs._links;
    this.customFieldsOnPublicPage = attrs.customFieldsOnPublicPage;

    if (ApiServiceBase) {
      this.ApiServiceBase = ApiServiceBase;
    }
  }

  get requiredFields(): Array<IdentifierField> {
    return [
      /* editable value, need handler */
      {
        key: "Creator Name",
        value: this.creatorName,
        // $FlowFixMe[incompatible-call]
        handler: (v) => this.setCreatorName(v),
      },
      {
        key: "Creator Type",
        value: this.creatorType,
        // $FlowFixMe[incompatible-call]
        handler: (v) => this.setCreatorType(v),
        radioOptions: creatorTypeOptions,
      },
      {
        key: "Name",
        value: this.title,
        // $FlowFixMe[incompatible-call]
        handler: (v) => this.setTitle(v),
      },
      {
        key: "Publisher",
        value: this.publisher,
        // $FlowFixMe[incompatible-call]
        handler: (v) => this.setPublisher(v),
      },
      {
        key: "Publication Year",
        value: this.publicationYear,
        // $FlowFixMe[incompatible-call]
        handler: (v) => this.setPublicationYear(v),
        isValid: (v) => typeof v === "string" && /^\d\d\d\d$/.test(v),
      },
      {
        key: "Resource Type",
        value: this.resourceType,
        // $FlowFixMe[incompatible-call]
        handler: (v) => this.setResourceType(v),
      },
    ];
  }

  get requiredCompleted(): boolean {
    return this.requiredFields.every((f) => Boolean(f.value));
  }

  /* some recommended fields may have required values */
  get optionalCompleted(): boolean {
    if (!this.alternateIdentifiers?.length && !this.geoLocations?.length)
      return true;
    return (
      (this.geoLocations && this.geoLocations.length > 0
        ? this.geoLocations.every((gl) => gl.isValid)
        : true) &&
      (this.alternateIdentifiers && this.alternateIdentifiers.length > 0
        ? this.alternateIdentifiers.every((id) => Boolean(id.freeType))
        : true)
    );
  }

  get isValid(): boolean {
    return this.requiredCompleted && this.optionalCompleted;
  }

  get recommendedFields(): Array<IdentifierField> {
    return [
      /**
       * editable value, need handler
       * handler performs array replacement
       * duplicates in values array are not allowed
       */
      {
        key: "Subjects",
        value: this.subjects,
        // $FlowFixMe[incompatible-call]
        handler: (v) => this.setSubjects(v),
      },
      {
        key: "Descriptions",
        value: this.descriptions,
        // $FlowFixMe[incompatible-call]
        handler: (v) => this.setDescriptions(v),
        options: identifierDescriptionOptions,
        selectAriaLabel: "Description Type",
      },
      {
        key: "Alternate Identifiers",
        value: this.alternateIdentifiers,
        // $FlowFixMe[incompatible-call]
        handler: (v) => this.setAlternateIdentifiers(v),
      },
      {
        key: "Dates",
        value: this.dates,
        // $FlowFixMe[incompatible-call]
        handler: (v) => this.setDates(v),
        options: identifierDateOptions,
        selectAriaLabel: "Event Type",
      },
      {
        key: "Geolocations",
        value: this.geoLocations,
        handler: (v) => {
          if (Array.isArray(v))
            this.setGeoLocations(ArrayUtils.filterClass(GeoLocationModel, v));
        },
      },
    ];
  }

  get anyRecommendedGiven(): boolean {
    return [
      this.subjects,
      this.descriptions,
      this.alternateIdentifiers,
      this.dates,
      this.geoLocations,
    ].some(
      (recommendedGroup) =>
        Array.isArray(recommendedGroup) && recommendedGroup.length > 0
    );
  }

  get doiTypeLabel(): string {
    return this.doiType.split("_")[1];
  }

  setEditing(value: boolean) {
    this.editing = value;
  }

  setCreatorName(name: string) {
    this.creatorName = name;
  }

  setCreatorType(type: CreatorType) {
    this.creatorType = type;
  }

  setTitle(title: string) {
    this.title = title;
  }

  setPublisher(publisher: string) {
    this.publisher = publisher;
  }

  setPublicationYear(year: string) {
    this.publicationYear = year;
  }

  setResourceType(type: string) {
    this.resourceType = type;
  }

  updateState(value: IGSNPublishingState) {
    this.state = value;
  }

  setSubjects(subjects: Array<IdentifierSubject>) {
    this.subjects = subjects;
  }

  setDescriptions(descriptions: Array<IdentifierDescription>) {
    this.descriptions = descriptions;
  }

  setAlternateIdentifiers(alternateIdentifiers: Array<AlternateIdentifier>) {
    this.alternateIdentifiers = alternateIdentifiers;
  }

  setDates(dates: Array<IdentifierDate>) {
    this.dates = dates;
  }

  setGeoLocations(geoLocations: $ReadOnlyArray<GeoLocation>) {
    this.geoLocations = geoLocations;
  }

  /*
   * We pass in various functions that would normally be pulled directly from
   * the UiStore as this class is used on the public page where the global
   * stores are not available as the user is not authenticated.
   */
  async publish({
    confirm,
    addAlert,
  }: {|
    confirm: (Node, Node, string, string) => Promise<boolean>,
    addAlert: (Alert) => void,
  |}): Promise<void> {
    if (!this.ApiServiceBase)
      throw new Error("This operation requires the user be authenticated");
    const ApiServiceBase = this.ApiServiceBase;
    try {
      if (
        await confirm(
          "You are about to publish this Identifier",
          <>
            The IGSN ID landing page, DataCite Commons, and the DataCite APIs
            will be updated with these changes.
            <br />
            <br />
            <strong>
              Please ensure the IGSN ID metadata you provided does not contain
              any information you do not want to make public before publishing,
              as this action cannot be fully undone.
            </strong>
            <br />
            <br />
            Do you want to proceed?
          </>,
          "OK",
          "CANCEL"
        )
      ) {
        if (!this.id) throw new Error("DOI Id must be known.");
        const response = await ApiServiceBase.post<
          {||},
          {
            state: IGSNPublishingState,
            url: string,
            publicUrl: string,
            creatorAffiliation: ?string,
            creatorAffiliationIdentifier: ?string,
          }
        >(`/identifiers/${this.id}/publish`, {});
        const {
          state,
          url,
          publicUrl,
          creatorAffiliation,
          creatorAffiliationIdentifier,
        } = response.data;
        this.updateState(state);
        if (state === "findable") {
          this.creatorAffiliation = creatorAffiliation;
          this.creatorAffiliationIdentifier = creatorAffiliationIdentifier;
        }
        runInAction(() => {
          this.url = url;
          this.publicUrl = publicUrl;
        });
        addAlert(
          mkAlert({
            message: `The identifier ${this.doi} has been published.`,
            variant: "success",
          })
        );
      }
    } catch (error) {
      // in case of errors like 422 the server provides a specific response message that we want to display
      const serverErrorResponse = error.response?.data;
      addAlert(
        mkAlert({
          title: `The identifier could not be published.`,
          message:
            serverErrorResponse?.message ?? error.message ?? "Unknown reason.",
          variant: "error",
        })
      );
      throw new Error(
        `An error occurred while publishing the identifier: ${error}`
      );
    }
  }

  /*
   * We pass in various functions that would normally be pulled directly from
   * the UiStore as this class is used on the public page where the global
   * stores are not available as the user is not authenticated.
   */
  async retract({
    confirm,
    addAlert,
  }: {|
    confirm: (Node, Node, string, string) => Promise<boolean>,
    addAlert: (Alert) => void,
  |}): Promise<void> {
    if (!this.ApiServiceBase)
      throw new Error("This operation requires the user be authenticated");
    const ApiServiceBase = this.ApiServiceBase;
    try {
      if (
        await confirm(
          "You are about to retract this Identifier",
          <>
            The IGSN ID will be set to <strong>Registered</strong>. It will be
            removed from DataCite Commons and the Public API, and the landing
            page will not display any metadata.
            <br />
            <br />
            <strong>
              The metadata will remain visible to other DataCite Members via the
              Member API.
            </strong>
            <br />
            <br />
            Do you want to proceed?
          </>,
          "OK",
          "CANCEL"
        )
      ) {
        if (!this.id) throw new Error("DOI Id must be known.");
        const response = await ApiServiceBase.post<
          {||},
          { state: IGSNPublishingState }
        >(`/identifiers/${this.id}/retract`, {});
        this.updateState(response.data.state);
        addAlert(
          mkAlert({
            message: `The identifier ${this.doi} has been retracted.`,
            variant: "success",
          })
        );
      }
    } catch (error) {
      const serverErrorResponse = error.response.data;
      addAlert(
        mkAlert({
          title: `The identifier could not be retracted.`,
          message:
            serverErrorResponse.message ?? error.message ?? "Unknown reason.",
          variant: "error",
        })
      );
      throw new Error(
        `An error occurred while retracting the identifier: ${error}`
      );
    }
  }

  /**
   * This method simply calls both the /retract and then the /publish endpoints,
   * so that the UI may provide a single button to trigger the two actions one
   * after the other. This is simply a usability enhancement as it may not be
   * immediately obvious to users that they must retract the IGSN to be able to
   * update the metadata. The reason that this method doesn't just call the
   * `retract` and `publish` methods above is that we don't need to display
   * alerts because there is no change to the visibility of the data and the
   * error handling is slightly more complex.
   *
   * We pass in various functions that would normally be pulled directly from
   * the UiStore as this class is used on the public page where the global
   * stores are not available as the user is not authenticated.
   */
  async republish({
    addAlert,
  }: {|
    addAlert: (Alert) => void,
  |}): Promise<void> {
    if (!this.ApiServiceBase)
      throw new Error("This operation requires the user be authenticated");
    const ApiServiceBase = this.ApiServiceBase;
    if (this.id === null || typeof this.id === "undefined") {
      addAlert(
        mkAlert({
          title: "Identifier could not be re-published",
          message: "DOI must be known",
          variant: "error",
        })
      );
    } else {
      const id: number = this.id;

      // retract
      try {
        const response = await ApiServiceBase.post<
          {||},
          { state: IGSNPublishingState }
        >(`/identifiers/${id}/retract`, {});
        this.updateState(response.data.state);
      } catch (error) {
        const serverErrorResponse = error.response.data;
        addAlert(
          mkAlert({
            title: `The identifier could not be republished.`,
            message:
              serverErrorResponse.message ?? error.message ?? "Unknown reason.",
            variant: "error",
          })
        );
        throw new Error(
          `An error occurred while retracting the identifier: ${error}`
        );
      }

      // publish
      try {
        const response = await ApiServiceBase.post<
          {||},
          {
            state: IGSNPublishingState,
            url: string,
            publicUrl: string,
            creatorAffiliation: ?string,
            creatorAffiliationIdentifier: ?string,
          }
        >(`/identifiers/${id}/publish`, {});
        const {
          state,
          url,
          publicUrl,
          creatorAffiliation,
          creatorAffiliationIdentifier,
        } = response.data;
        this.updateState(state);
        if (state === "findable") {
          this.creatorAffiliation = creatorAffiliation;
          this.creatorAffiliationIdentifier = creatorAffiliationIdentifier;
        }
        runInAction(() => {
          this.url = url;
          this.publicUrl = publicUrl;
        });
        addAlert(
          mkAlert({
            message: `The identifier ${this.doi} has been republished.`,
            variant: "success",
          })
        );
      } catch (error) {
        /*
         * It is possible, although unlikely, that the retract step could succeed and then the
         * publish step could fail. Should that happen, the identifier will be left in a registered
         * state and the user will need to manually re-trigger a publish step.
         */
        const serverErrorResponse = error.response?.data;
        addAlert(
          mkAlert({
            title: `The identifier could not be republished.`,
            message:
              "Identifier has been retracted. Tap publish to try again.\n" +
              (serverErrorResponse?.message ??
                error.message ??
                "Unknown reason."),
            variant: "error",
          })
        );
        throw new Error(
          `An error occurred while republishing the identifier: ${error}`
        );
      }
    }
  }

  toJson(): { ... } {
    return {
      parentGlobalId: this.parentGlobalId,
      id: this.id,
      rsPublicId: this.rsPublicId,
      doi: this.doi,
      doiType: this.doiType,
      creatorName: this.creatorName,
      creatorType: this.creatorType,
      creatorAffiliation: this.creatorAffiliation,
      creatorAffiliationIdentifier: this.creatorAffiliationIdentifier,
      title: this.title,
      publicUrl: this.publicUrl,
      publisher: this.publisher,
      publicationYear: this.publicationYear,
      resourceType: this.resourceType,
      resourceTypeGeneral: this.resourceTypeGeneral,
      url: this.url,
      state: this.state,
      subjects: this.subjects,
      descriptions: this.descriptions,
      alternateIdentifiers: this.alternateIdentifiers,
      dates: this.dates,
      geoLocations: this.geoLocations?.map((g) => g.toJson()),
      editing: this.editing,
      customFieldsOnPublicPage: this.customFieldsOnPublicPage,
    };
  }
}
