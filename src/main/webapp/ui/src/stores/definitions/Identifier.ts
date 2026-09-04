import type React from "react";
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
 * Catalog key for a publication state's label: the same words the identifiers table shows in its
 * State cell. Null for a state RSpace does not recognise, since the server passes an unrecognised
 * provider status through verbatim and the caller has to show that raw value rather than throwing
 * out of a render.
 *
 * Shared so the table and the alerts cannot disagree. They used to be produced separately, which
 * put the provider's raw English token inside an otherwise translated sentence.
 *
 * Returns the key rather than the translated text, so each caller translates with its own `t`.
 * That keeps this module clear of the i18n singleton and lets a component's label follow a
 * language change. Arms are spelled out rather than built from the state, so the typed catalog and
 * the i18n extractor both still see every key.
 */
export const identifierStateLabelKey = (state: PublishingState) => {
  switch (state) {
    case "draft":
      return "inventory:igsnTable.filters.stateOptions.draft.title";
    case "findable":
      return "inventory:igsnTable.filters.stateOptions.findable.title";
    case "registered":
      return "inventory:igsnTable.filters.stateOptions.registered.title";
    case "created":
      return "inventory:fields.identifiers.list.stateLabels.created";
    case "submitted":
      return "inventory:fields.identifiers.list.stateLabels.submitted";
    case "accepted":
      return "inventory:fields.identifiers.list.stateLabels.accepted";
    case "declined":
      return "inventory:fields.identifiers.list.stateLabels.declined";
    case "cancelled":
      return "inventory:fields.identifiers.list.stateLabels.cancelled";
    case "expired":
      return "inventory:fields.identifiers.list.stateLabels.expired";
    default:
      return null;
  }
};

/**
 * Machine-readable result of one external PIDINST metadata update attempt (RSDEV-1356). UPDATED:
 * the provider accepted the rebuilt metadata. FAILED: it could not be reached or rejected the
 * update, or the payload could not be built; the instrument is saved and saving again retries.
 * NOT_UPDATABLE: the record's own state no longer allows an in-place update, which is normal and
 * expected rather than an error.
 */
export type ExternalMetadataUpdateOutcome = "UPDATED" | "FAILED" | "NOT_UPDATABLE";

/**
 * Outcome of the external PIDINST metadata update attempted by the save or transfer that returned
 * this identifier (ADR 0008 item 4). Present only when a push was attempted; absent means nothing
 * was attempted and must never be shown as a failure. Response-only: the next read of the same
 * identifier does not carry it. `reason` is a localized sentence from the server, ready to show.
 */
export type ExternalMetadataUpdate = {
  outcome: ExternalMetadataUpdateOutcome;
  reason: string;
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
  externalMetadataUpdate?: ExternalMetadataUpdate | null;
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
  externalMetadataUpdate?: ExternalMetadataUpdate | null;

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
