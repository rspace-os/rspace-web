import { expect } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { uniqueName } from "@/__tests__/e2e/testData";

test.describe("Inventory Instrument CSV import API", () => {
  test("As an API client, I can import an Instrument from a CSV file mapped onto an Instrument Template's custom field", async ({
    clientInventory,
  }) => {
    const templateName = uniqueName("e2e-csv-import-instrument-template");
    const instrumentName = uniqueName("e2e-csv-import-instrument");

    const template = await clientInventory.createInstrumentTemplate({
      name: templateName,
      fields: [{ name: "Serial Number", type: "text" }],
    });

    const csv = `Name,Serial Number\n${instrumentName},SN-001\n`;

    const result = await clientInventory.importInstruments(
      { name: "instruments.csv", mimeType: "text/csv", buffer: Buffer.from(csv) },
      { templateId: template.id, fieldMappings: { Name: "name" } },
    );

    expect(result.status).toBe("COMPLETED");
    expect(result.instrumentResults?.errorCount).toBe(0);
    expect(result.instrumentResults?.successCount).toBe(1);

    const created = result.instrumentResults?.results[0]?.record;
    expect(created?.name).toBe(instrumentName);
    expect(created?.globalId).toMatch(/^IN\d+$/);
  });
});
