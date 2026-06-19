import { describe, expect, it } from "vitest";
import { apiStringToFieldType, FIELD_LABEL, FieldTypes, fieldTypeToApiString } from "../FieldTypes";

describe("FieldTypes - link", () => {
  it("maps the link symbol to and from the 'link' api string", () => {
    expect(fieldTypeToApiString(FieldTypes.link)).toBe("link");
    expect(apiStringToFieldType("link")).toBe(FieldTypes.link);
  });

  it("has a human-readable label for the link field type", () => {
    expect(FIELD_LABEL[FieldTypes.link]).toBe("Link");
  });
});
