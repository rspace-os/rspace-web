//@flow
import { type Id, type GlobalId } from "./BaseRecord";
import { type GeoLocation, type GeoLocationAttrs } from "./GeoLocation";
import { type URL } from "../../util/types";
import { type _LINK } from "../../common/ApiServiceBase";
import { type RadioOption } from "../../components/Inputs/RadioField";

export type IGSNDateType =
  | "Accepted"
  | "Available"
  | "Copyrighted"
  | "Collected"
  | "Created"
  | "Issued"
  | "Submitted"
  | "Updated"
  | "Valid"
  | "Withdrawn"
  | "Other";
export type IGSNDescriptionType = "ABSTRACT" | "METHODS";

type IGSNTypeOption = IGSNDescriptionType | IGSNDateType;

export type DropdownOption = {|
  value: string | IGSNTypeOption,
  label: string,
|};

export type IdentifierField = {|
  key: string,
  value: mixed,

  handler?: (v: mixed) => void,
  options?: Array<DropdownOption>,
  selectAriaLabel?: string,
  radioOptions?: Array<RadioOption<string>>,
  isValid?: (v: mixed) => boolean,
|};

export type IdentifierSubject = {
  value: string,
  subjectScheme: ?string,
  schemeURI: ?string,
  valueURI: ?string,
  classificationCode: ?string,
};

export type IdentifierDescription = {
  value: string,
  type: IGSNDescriptionType,
};

export type AlternateIdentifier = {
  value: string,
  freeType: string,
};

export type CreatorType = "Personal" | "Organizational";

export type IGSNPublishingState = "draft" | "findable" | "registered";

export type IdentifierDate = { value: Date, type: IGSNDateType };

/*
 * This is the shape of the data output by the server to model an identifier
 * that has been persisted in the database.
 */
export type IdentifierAttrs = {|
  id: Id,
  rsPublicId: ?string,
  doi: string,
  doiType: string,
  creatorName: string,
  creatorType: CreatorType,
  creatorAffiliation: ?string,
  creatorAffiliationIdentifier: ?string,
  title: string,
  publicUrl: ?URL,
  publisher: string,
  publicationYear: number,
  resourceType: string,
  resourceTypeGeneral: string,
  url: ?URL,
  state: IGSNPublishingState,
  subjects: ?Array<IdentifierSubject>,
  descriptions: ?Array<IdentifierDescription>,
  alternateIdentifiers: ?Array<AlternateIdentifier>,
  dates: ?Array<IdentifierDate>,
  geoLocations: ?Array<GeoLocationAttrs>,
  _links: Array<_LINK>,
  customFieldsOnPublicPage: boolean,
|};

export interface Identifier {
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
  state: IGSNPublishingState;
  subjects: ?Array<IdentifierSubject>;
  descriptions: ?Array<IdentifierDescription>;
  alternateIdentifiers: ?Array<AlternateIdentifier>;
  dates: ?Array<IdentifierDate>;
  geoLocations: ?Array<GeoLocation>;
  customFieldsOnPublicPage: boolean;
  _links: Array<_LINK>;

  +doiTypeLabel: string;
  +isValid: boolean;
  +requiredFields: Array<IdentifierField>;
  +recommendedFields: Array<IdentifierField>;
  +anyRecommendedGiven: boolean;

  publish(): Promise<void>;
  retract(): Promise<void>;
  republish(): Promise<void>;

  /*
   * This computed property is for showing the Identifier in the public page
   * preview dialog. The public page itself renders IdentifierAttrs so to be
   * compatible any implementations of this interface must expose an object
   * with the same type.
   */
  +publicData: IdentifierAttrs;
}
