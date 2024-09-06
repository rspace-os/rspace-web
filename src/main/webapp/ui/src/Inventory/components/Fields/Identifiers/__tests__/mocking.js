// @flow
import {
  type Identifier,
  type IdentifierAttrs,
} from "../../../../../stores/definitions/Identifier";

type TestRecordType = "sample" | "container"; // render for subSample like for sample

export const mockIGSNAttrs = (): IdentifierAttrs => {
  return {
    id: 1,
    rsPublicId: "WywYf6xh2FRTisfMx0y4BA",
    doi: "/10.82316/2z52-vx20",
    doiType: "DATACITE_IGSN",
    creatorName: "User 1",
    creatorType: "Personal",
    creatorAffiliation:
      "Association of Asian Pacific Community Health Organizations",
    creatorAffiliationIdentifier: "https://ror.org/03zsq2967",
    title: "Item one",
    publicUrl: "https://doi.org/10.82316/2z52-vx20",
    publisher: "ResearchSpace at http://localhost:8080",
    publicationYear: 2023,
    resourceType: "Material Sample",
    resourceTypeGeneral: "PhysicalObject",
    url: "http://localhost:8080/public/inventory/WywYf6xh2FRTisfMx0y4BA",
    state: "findable",
    _links: [],

    /* Recommended Fields (can be null) */
    subjects: [
      {
        classificationCode: "a code",
        schemeURI: "https://uri.one",
        subjectScheme: "test scheme",
        value: "Subject one",
        valueURI: "https://uri.two",
      },
    ],
    descriptions: [{ value: "My test description text", type: "ABSTRACT" }],
    alternateIdentifiers: [{ value: "SS4", freeType: "a-type" }],
    dates: [{ value: "2023-08-09T16:32:17.853Z", type: "CREATED" }],
    geoLocations: [],
    customFieldsOnPublicPage: false,
  };
};

const itemData = (recordType: TestRecordType) => {
  return {
    recordType,
    globalId: recordType === "sample" ? "SA1" : "IC1",
    description: "An Inventory Record for testing",
  };
};

export const mockIGSNIdentifier = (recordType: TestRecordType): Identifier => {
  return {
    parentGlobalId: itemData(recordType).globalId,
    id: 1,
    rsPublicId: "WywYf6xh2FRTisfMx0y4BA",
    doi: "/10.82316/2z52-vx20",
    doiType: "DATACITE_IGSN",
    creatorName: "User 1",
    creatorType: "Personal",
    creatorAffiliation:
      "Association of Asian Pacific Community Health Organizations",
    creatorAffiliationIdentifier: "https://ror.org/03zsq2967",
    title: "Item one",
    publicUrl: "https://doi.org/10.82316/2z52-vx20",
    publisher: "ResearchSpace at http://localhost:8080",
    publicationYear: "2023",
    resourceType: "Material Sample",
    resourceTypeGeneral: "PhysicalObject",
    url: "http://localhost:8080/public/inventory/WywYf6xh2FRTisfMx0y4BA",
    state: "findable",
    _links: [],

    /* Recommended Fields (can be null) */
    subjects: [
      {
        classificationCode: "a code",
        schemeURI: "https://uri.one",
        subjectScheme: "test scheme",
        value: "Subject one",
        valueURI: "https://uri.two",
      },
    ],
    descriptions: [{ value: "My test description text", type: "ABSTRACT" }],
    alternateIdentifiers: [{ value: "SS4", freeType: "a-type" }],
    dates: [{ value: new Date("2023-08-09T16:32:17.853Z"), type: "CREATED" }],
    geoLocations: [],

    doiTypeLabel: "Material Sample",
    isValid: true, // computed based on Identifier data
    anyRecommendedGiven: true, // computed based on Identifier data
    requiredFields: [],
    fixedFields: [],
    recommendedFields: [],
    hasChanged: false,
    customFieldsOnPublicPage: false,

    publish: async (): Promise<void> => {},
    retract: async (): Promise<void> => {},
    republish: async (): Promise<void> => {},

    toJson() {
      return {};
    },
  };
};
