import "@/stores/stores/RootStore";
import { describe, expect, test, vi } from "vitest";
import { makeMockSample } from "./mocking";

vi.mock("../../../../common/InvApiService", () => ({ default: {} }));

const ACCESS_PERMISSION_FIELDS = new Set(["sharingMode", "sharedWith"]);

describe("isFieldEditable", () => {
  test("access permissions stay locked without ownership control", () => {
    const sample = makeMockSample({ permittedActions: ["READ", "UPDATE"] });
    sample.setEditable(new Set(["name", ...ACCESS_PERMISSION_FIELDS]), true);
    expect(sample.isFieldEditable("name")).toBe(true);
    expect(sample.isFieldEditable("sharingMode")).toBe(false);
    expect(sample.isFieldEditable("sharedWith")).toBe(false);
  });

  test("access permissions are editable with ownership control", () => {
    const sample = makeMockSample();
    sample.setEditable(ACCESS_PERMISSION_FIELDS, true);
    expect(sample.isFieldEditable("sharingMode")).toBe(true);
    expect(sample.isFieldEditable("sharedWith")).toBe(true);
  });
});
