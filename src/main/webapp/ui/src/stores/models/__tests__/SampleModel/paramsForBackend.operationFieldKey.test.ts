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
 * generated; the server sets it and ignores it in every request. `paramsForBackend` builds an
 * explicit allowlist, so an ordinary save does not echo it back. Both halves are pinned here
 * because a refactor to `{...ef}` would look harmless and would start sending a read-only
 * property on every save of an operation-created sample.
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
    expect(sent.name).toEqual("Passage number");
    expect(sent.content).toEqual("4");
  });
});
