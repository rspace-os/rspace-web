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
            cancelLabel: string,
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
            cancelLabel: string,
        ) => Promise<boolean>;
        addAlert: (alert: Alert) => void;
    }): Promise<void>;
    republish({ addAlert }: { addAlert: (alert: Alert) => void }): Promise<void>;

    toJson(): object;
}
