import { describe, expect, test, vi } from "vitest";
import { makeMockSample } from "./mocking";

vi.mock("../../../use-stores", () => () => {});
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    unitStore: {
      getUnit: () => ({ label: "ml" }),
    },
  }),
}));

/**
 * `operationFieldKey` travels INBOUND only (RSDEV-1231).
 *
 * The API returns it so a later run of an operation can recognise the field a previous run
 * generated, and only the operations endpoint may set it. `paramsForBackend` builds an explicit
 * allowlist, so an ordinary save does not echo it back. Both halves are pinned here because a
 * refactor to `{...ef}` would look harmless and would start sending a property no other endpoint
 * accepts, on every save of an operation-created sample (parallel review, I18).
 */
describe("paramsForBackend and operationFieldKey", () => {
  const operationCreatedField = {
    id: 5,
    globalId: null,
    name: "Passage number",
    lastModified: null,
    type: "text" as const,
    content: "4",
    parentGlobalId: null,
    editing: false,
    initial: false,
    operationFieldKey: "operations.passage.numberField",
  };

  test("reads the key in from the API response", () => {
    // What computedValues' key-first matching depends on: if the model dropped it, the next Passage
    // would fall back to matching a localized name and restart the counter.
    const sample = makeMockSample();
    sample.addExtraField(operationCreatedField);
    expect(sample.extraFields[0].operationFieldKey).toEqual("operations.passage.numberField");
  });

  test("never sends it back", () => {
    const sample = makeMockSample();
    sample.currentlyEditableFields.add("extraFields");
    sample.addExtraField(operationCreatedField);

    const params = sample.paramsForBackend as {
      extraFields?: Array<Record<string, unknown>>;
    };
    const sent = params.extraFields?.[0] ?? {};
    expect(Object.keys(sent)).not.toContain("operationFieldKey");
    // The rest of the field is still sent, so this is an omission, not a dropped field.
    expect(sent.name).toEqual("Passage number");
    expect(sent.content).toEqual("4");
  });
});
