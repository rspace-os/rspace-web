import { type Id, type GlobalId } from "./BaseRecord";
import { type GeoLocation, type GeoLocationAttrs } from "./GeoLocation";
import { type URL, type _LINK } from "../../util/types";
import { type RadioOption } from "../../components/Inputs/RadioField";
import React from "react";
import { type Alert } from "../contexts/Alert";

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
  selectAriaLabel?: string;
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
  publisher: string;
  publicationYear: number;
  resourceType: string;
  resourceTypeGeneral: string;
  url: URL | null;
  state: IGSNPublishingState;
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
  publisher: string;
  publicationYear: string;
  resourceType: string;
  resourceTypeGeneral: string;
  url: URL | null;
  state: IGSNPublishingState;
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
  }: {
    confirm: (
      title: React.ReactNode,
      body: React.ReactNode,
      confirmLabel: string,
      cancelLabel: string
    ) => Promise<boolean>;
    addAlert: (alert: Alert) => void;
  }): Promise<void>;
  retract({
    confirm,
    addAlert,
  }: {
    confirm: (
      title: React.ReactNode,
      body: React.ReactNode,
      confirmLabel: string,
      cancelLabel: string
    ) => Promise<boolean>;
    addAlert: (alert: Alert) => void;
  }): Promise<void>;
  republish({ addAlert }: { addAlert: (alert: Alert) => void }): Promise<void>;

  toJson(): object;
}
