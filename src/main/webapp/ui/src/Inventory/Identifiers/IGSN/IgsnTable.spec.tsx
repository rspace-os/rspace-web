import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { worker } from "@/__tests__/browserSetup";
import { oauthTokenHandler } from "@/__tests__/mocks/inventoryMocks";
import { SimpleIgsnTable } from "./IgsnTable.story";
import { IgsnTablePage } from "./pageObjects/IgsnTablePage";

/*
 * The four identifiers returned by the mock API — matches
 * src/main/webapp/ui/src/Inventory/__tests__/identifiers.json exactly.
 */
const IDENTIFIERS_PAYLOAD = [
  {
    id: 3,
    doiType: "DATACITE_IGSN",
    doi: "10.82316/khma-em96",
    associatedGlobalId: "SA32768",
    creatorName: "user user",
    creatorType: "Personal",
    creatorAffiliation: null,
    creatorAffiliationIdentifier: null,
    title: "prt-907-1",
    publisher: "University of RSpace (Localhost)",
    publicationYear: 2025,
    state: "draft",
    resourceType: "Material Sample",
    resourceTypeGeneral: "PhysicalObject",
    url: "http://localhost:8080/public/inventory/uNc26-leAIjD45m-Q55SQg",
    subjects: null,
    descriptions: null,
    geoLocations: null,
    alternateIdentifiers: null,
    dates: null,
    rsPublicId: "uNc26-leAIjD45m-Q55SQg",
    publicUrl: null,
    customFieldsOnPublicPage: false,
    _links: [],
  },
  {
    id: 4,
    doiType: "DATACITE_IGSN",
    doi: "10.82316/jy8j-ts43",
    associatedGlobalId: "SA32769",
    creatorName: "user user",
    creatorType: "Personal",
    creatorAffiliation: null,
    creatorAffiliationIdentifier: null,
    title: "prt-907-2",
    publisher: "University of RSpace (Localhost)",
    publicationYear: 2025,
    state: "draft",
    resourceType: "Material Sample",
    resourceTypeGeneral: "PhysicalObject",
    url: "http://localhost:8080/public/inventory/Ee1FnIsnlFmcLi393KcBhw",
    subjects: null,
    descriptions: null,
    geoLocations: null,
    alternateIdentifiers: null,
    dates: null,
    rsPublicId: "Ee1FnIsnlFmcLi393KcBhw",
    publicUrl: null,
    customFieldsOnPublicPage: false,
    _links: [],
  },
  {
    id: 5,
    doiType: "DATACITE_IGSN",
    doi: "10.82316/hqkq-hr38",
    associatedGlobalId: "SA32770",
    creatorName: "user user",
    creatorType: "Personal",
    creatorAffiliation: null,
    creatorAffiliationIdentifier: null,
    title: "prt-907-3",
    publisher: "University of RSpace (Localhost)",
    publicationYear: 2025,
    state: "draft",
    resourceType: "Material Sample",
    resourceTypeGeneral: "PhysicalObject",
    url: "http://localhost:8080/public/inventory/j6t3oZv5B8Zs_FaM6u2eKQ",
    subjects: null,
    descriptions: null,
    geoLocations: null,
    alternateIdentifiers: null,
    dates: null,
    rsPublicId: "j6t3oZv5B8Zs_FaM6u2eKQ",
    publicUrl: null,
    customFieldsOnPublicPage: false,
    _links: [],
  },
  {
    id: 6,
    doiType: "DATACITE_IGSN",
    doi: "10.82316/cn5w-1959",
    associatedGlobalId: "SA32773",
    creatorName: "user user",
    creatorType: "Personal",
    creatorAffiliation: null,
    creatorAffiliationIdentifier: null,
    title: "prt-907-6",
    publisher: "University of RSpace (Localhost)",
    publicationYear: 2025,
    state: "draft",
    resourceType: "Material Sample",
    resourceTypeGeneral: "PhysicalObject",
    url: "http://localhost:8080/public/inventory/LB1_7qaSiviUukEUfBsU8g",
    subjects: null,
    descriptions: null,
    geoLocations: null,
    alternateIdentifiers: null,
    dates: null,
    rsPublicId: "LB1_7qaSiviUukEUfBsU8g",
    publicUrl: null,
    customFieldsOnPublicPage: false,
    _links: [],
  },
];

const table = new IgsnTablePage();

beforeEach(() => {
  worker.use(
    oauthTokenHandler(),
    http.get("/api/inventory/v1/identifiers", () => HttpResponse.json(IDENTIFIERS_PAYLOAD)),
  );
});

afterEach(() => {
  cleanup();
});

describe("IGSN Table", () => {
  test("When there is no selection, all rows should be included in the export.", async () => {
    render(<SimpleIgsnTable />);
    await table.waitForLoad();
    // Ensure every row has rendered before exporting, otherwise a slow engine
    // can export a partial CSV.
    await table.waitForRowCount(4);
    const csv = await table.exportToCsv();
    const lines = csv.split("\n");
    // 4 data rows + 1 header row
    expect(lines.length).toBe(4 + 1);
  });

  test("When some IGSNs are selected, CSV exports should include just those rows", async () => {
    render(<SimpleIgsnTable />);
    await table.waitForLoad();
    await table.waitForRowCount(4);
    // Select 2 rows
    await table.selectRowByIndex(0);
    await table.selectRowByIndex(1);
    const csv = await table.exportToCsv();
    const lines = csv.split("\n");
    // 2 selected rows + 1 header row
    expect(lines.length).toBe(2 + 1);
  });
});
