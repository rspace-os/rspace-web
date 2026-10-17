import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { runInNewContext } from "node:vm";
import { expect, it } from "vitest";
import { legacyMsg } from "@/__tests__/helpers/legacyI18n";

it.each([
  { description: "", expected: "Purpose / notes: Calibration" },
  { description: "permanent=true", expected: "Purpose / notes: Calibration; permanent=true" },
])("shows server booking details alongside '$description'", ({ description, expected }) => {
  const event = {
    action: "DELETE",
    domain: "BOOKING",
    description,
    details: "Purpose / notes: Calibration",
    displayAction: "",
  };
  const response = { data: { results: [{ timestamp: 0, event, data: { action: "DELETE" } }] } };
  const source = readFileSync(resolve(__dirname, "../../../scripts/pages/audit.js"), "utf8");

  runInNewContext(`${source}\n_convertAuditTrailResults(response);`, {
    $: () => ({ ready: () => undefined }),
    document: {},
    RS: { msg: legacyMsg },
    response,
  });

  expect(event.description).toBe(expected);
  expect(event.displayAction).toBe(description ? legacyMsg("legacyjs.audit.permanentlyDeleted") : "DELETE");
});
