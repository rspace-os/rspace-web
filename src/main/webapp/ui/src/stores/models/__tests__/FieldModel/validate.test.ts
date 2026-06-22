import "@/stores/stores/RootStore";
import { describe, expect, test, vi } from "vitest";
import FieldModel from "../../FieldModel";
import { makeMockSample } from "../SampleModel/mocking";

vi.mock("../../../../common/InvApiService", () => ({ default: {} }));
describe("method: validState", () => {
  test("Error flag should be asserted.", () => {
    const field = new FieldModel(
      {
        attachment: null,
        columnIndex: 1,
        content: "2",
        definition: null,
        globalId: "SF19",
        id: 19,
        mandatory: false,
        name: "MyNumber",
        selectedOptions: null,
        type: "number",
      },
      makeMockSample(),
    );

    field.setError(true);
    expect(field.validate().isOk).toBe(false);
  });

  test("A link field with unapplied editor changes reports a link-specific reason.", () => {
    const field = new FieldModel(
      {
        attachment: null,
        columnIndex: 1,
        content: "",
        definition: null,
        globalId: "SF20",
        id: 20,
        mandatory: false,
        name: "Related item",
        selectedOptions: null,
        type: "link",
        link: {
          relationType: "References",
          targetGlobalId: "SA2",
          versionPin: null,
        },
      },
      makeMockSample(),
    );

    // the link editor flags the model while its staged state is unapplied
    field.setError(true);

    const result = field.validate();
    expect(result.isOk).toBe(false);
    const message = result.orElseGet((errors) => errors.map((e) => e.message).join(" "));
    expect(message).toMatch(/apply or discard/i);
  });
});
