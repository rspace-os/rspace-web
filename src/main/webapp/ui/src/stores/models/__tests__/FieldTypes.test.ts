import { describe, expect, it } from "vitest";
import {
  apiStringToFieldType,
  compatibleFieldTypes,
  FIELD_LABEL,
  FieldTypes,
  fieldTypeToApiString,
} from "../FieldTypes";

describe("FieldTypes - link", () => {
  it("maps the link symbol to and from the 'link' api string", () => {
    expect(fieldTypeToApiString(FieldTypes.link)).toBe("link");
    expect(apiStringToFieldType("link")).toBe(FieldTypes.link);
  });

  it("has a human-readable label for the link field type", () => {
    expect(FIELD_LABEL[FieldTypes.link]).toBe("Link");
  });

  it("offers link as a type the user can choose for an imported column", () => {
    // detection only types a column as link when every present value parses, so a column whose
    // values do parse but which was typed as something else has no other route to link
    expect(compatibleFieldTypes(FieldTypes.plain_text)).toContain(FieldTypes.link);
    expect(compatibleFieldTypes(FieldTypes.formatted_text)).toContain(FieldTypes.link);
    expect(compatibleFieldTypes(FieldTypes.uri)).toContain(FieldTypes.link);
  });

  it("keeps link selectable for a column already detected as link", () => {
    expect(compatibleFieldTypes(FieldTypes.link)).toContain(FieldTypes.link);
  });

  it("does not offer link for reference or attachment columns", () => {
    // these two carry a record handle rather than a cell value, so no text-like type is offered
    // for them and link must not be either
    expect(compatibleFieldTypes(FieldTypes.reference)).not.toContain(FieldTypes.link);
    expect(compatibleFieldTypes(FieldTypes.attachment)).not.toContain(FieldTypes.link);
  });
});
