import type React from "react";
import i18n from "@/modules/common/i18n";
import type { RadioOption } from "../../components/Inputs/RadioField";
import type { _LINK, URL } from "../../util/types";
import type { Alert } from "../contexts/Alert";
import type { GlobalId, Id } from "./BaseRecord";
import type { GeoLocation, GeoLocationAttrs } from "./GeoLocation";

/**
 * @module Identifier
 * @description There are various global identifier schemes used across the
 * scientific research community to uniquely identifier everything from research
 * outputs to the researchers themselves. This module is particularly concerned
 * with identifiers used on samples, for which RSpace currently supports just
 * IGSN IDs. When an RSpace instance is configured with DataCite, researchers
 * may "mint" an identifier to associate with any Inventory record. Once published,
 * the identifier receives a DOI from DataCite, is registered in the DataCite
 * metadata store, and has a public Webpage. This module models the types used
 * across the frontend system for identifiers.
 */

export type IGSNDateType =
  | "ACCEPTED"
  | "AVAILABLE"
  | "COPYRIGHTED"
  | "COLLECTED"
  | "CREATED"
  | "ISSUED"
  | "SUBMITTED"
  | "UPDATED"
  | "VALID"
  | "WITHDRAWN"
  | "OTHER";
export type IGSNDescriptionType = "ABSTRACT" | "METHODS";

export type DropdownOption = {
  value: string; // typically IGSNDescriptionType | IGSNDateType
  label: string;
};

export type IdentifierField = {
  key: string;
  value: unknown;

  handler?: (v: unknown) => void;
  options?: Array<DropdownOption>;
  selectLabelLabel?: string;
  radioOptions?: Array<RadioOption<string>>;
  isValid?: (v: unknown) => boolean;
};

export type IdentifierSubject = {
  value: string;
  subjectScheme: string | null;
  schemeURI: string | null;
  valueURI: string | null;
  classificationCode: string | null;
};

export type IdentifierDescription = {
  value: string;
  type: IGSNDescriptionType;
};

export type AlternateIdentifier = {
  value: string;
  freeType: string;
};

export type CreatorType = "Personal" | "Organizational";

export type IGSNPublishingState = "draft" | "findable" | "registered";

/**
 * PIDINST identifiers registered with B2INST are published by submitting the record to a community
 * for curator review. The server stores the Invenio review-request status verbatim, so these are
 * the states a PIDINST_B2INST identifier can report in place of the DataCite ones above.
 */
export type PidinstPublishingState = "created" | "submitted" | "accepted" | "declined" | "cancelled" | "expired";

/**
 * Any state an identifier can report. Both providers share {@link IGSNPublishingState}'s "draft";
 * everything else is provider-specific, so consumers must tolerate a state they do not model.
 */
export type PublishingState = IGSNPublishingState | PidinstPublishingState;

/**
 * True when the identifier's metadata is published: DataCite's "findable", or B2INST's
 * "accepted" (the community review outcome that publishes the record).
 */
export const isPublishedState = (state: PublishingState): boolean => state === "findable" || state === "accepted";

/**
 * The translated label for a publication state: the same words the identifiers table shows in its
 * State cell.
 *
 * <p>Shared so the table and the alerts cannot disagree. The state cell and the refresh alert used
 * to be produced separately, which showed the provider's raw English token inside an otherwise
 * translated sentence.
 *
 * Arms are spelled out rather than built from the state, so the typed catalog and the i18n
 * extractor both still see every key. The final fallback is deliberate: the server passes an
 * unrecognised provider status through verbatim, and that must degrade to the raw value rather
 * than throwing out of a render.
 */
export const identifierStateLabel = (state: PublishingState): string => {
  switch (state) {
    case "draft":
      return i18n.t("inventory:igsnTable.filters.stateOptions.draft.title");
    case "findable":
      return i18n.t("inventory:igsnTable.filters.stateOptions.findable.title");
    case "registered":
      return i18n.t("inventory:igsnTable.filters.stateOptions.registered.title");
    case "created":
      return i18n.t("inventory:fields.identifiers.list.stateLabels.created");
    case "submitted":
      return i18n.t("inventory:fields.identifiers.list.stateLabels.submitted");
    case "accepted":
      return i18n.t("inventory:fields.identifiers.list.stateLabels.accepted");
    case "declined":
      return i18n.t("inventory:fields.identifiers.list.stateLabels.declined");
    case "cancelled":
      return i18n.t("inventory:fields.identifiers.list.stateLabels.cancelled");
    case "expired":
      return i18n.t("inventory:fields.identifiers.list.stateLabels.expired");
    default:
      return String(state);
  }
};

export type IdentifierDate = { value: Date; type: IGSNDateType };

/*
 * This is the shape of the data output by the server to model an identifier
 * that has been persisted in the database.
 */
export type IdentifierAttrs = {
  id: Id;
  rsPublicId: string | null;
  doi: string;
  doiType: string;
  creatorName: string;
  creatorType: CreatorType;
  creatorAffiliation: string | null;
  creatorAffiliationIdentifier: string | null;
  title: string;
  publicUrl: URL | null;

  /**
   * The record's page on the issuing provider (for example the B2INST deposit page). Present from
   * registration onwards, unlike {@link publicUrl}, and may require signing in to that provider.
   */
  providerUrl: URL | null;
  publisher: string;
  publicationYear: number;
  resourceType: string;
  resourceTypeGeneral: string;
  url: URL | null;
  state: PublishingState;
  subjects: Array<IdentifierSubject> | null;
  descriptions: Array<IdentifierDescription> | null;
  alternateIdentifiers: Array<AlternateIdentifier> | null;
  dates: Array<{ value: string; type: IGSNDateType }> | null;
  geoLocations: Array<GeoLocationAttrs> | null;
  _links: Array<_LINK>;
  customFieldsOnPublicPage: boolean;
};

export interface Identifier {
  parentGlobalId: GlobalId;
  id: Id;
  rsPublicId: string | null;
  doi: string;
  doiType: string;
  creatorName: string;
  creatorType: CreatorType;
  creatorAffiliation: string | null;
  creatorAffiliationIdentifier: string | null;
  title: string; // item.name
  publicUrl: URL | null;

  /**
   * The record's page on the issuing provider (for example the B2INST deposit page). Present from
   * registration onwards, unlike {@link publicUrl}, and may require signing in to that provider.
   */
  providerUrl: URL | null;
  publisher: string;
  publicationYear: string;
  resourceType: string;
  resourceTypeGeneral: string;
  url: URL | null;
  state: PublishingState;
  subjects: Array<IdentifierSubject> | null;
  descriptions: Array<IdentifierDescription> | null;
  alternateIdentifiers: Array<AlternateIdentifier> | null;
  dates: Array<IdentifierDate> | null;
  geoLocations: ReadonlyArray<GeoLocation> | null;
  customFieldsOnPublicPage: boolean;
  _links: Array<_LINK>;

  readonly doiTypeLabel: string;
  readonly isValid: boolean;
  readonly requiredFields: Array<IdentifierField>;
  readonly recommendedFields: Array<IdentifierField>;
  readonly anyRecommendedGiven: boolean;

  publish({
    confirm,
    addAlert,
    onPublished,
  }: {
    confirm: (
      title: React.ReactNode,
      body: React.ReactNode,
      confirmLabel: string,
      cancelLabel: string,
    ) => Promise<boolean>;
    addAlert: (alert: Alert) => void;
    onPublished?: () => void;
  }): Promise<void>;
  retract({
    confirm,
    addAlert,
  }: {
    confirm: (
      title: React.ReactNode,
      body: React.ReactNode,
      confirmLabel: string,
      cancelLabel: string,
    ) => Promise<boolean>;
    addAlert: (alert: Alert) => void;
  }): Promise<void>;
  republish({ addAlert }: { addAlert: (alert: Alert) => void }): Promise<void>;
  refresh({ addAlert }: { addAlert: (alert: Alert) => void }): Promise<void>;

  toJson(): object;
}
