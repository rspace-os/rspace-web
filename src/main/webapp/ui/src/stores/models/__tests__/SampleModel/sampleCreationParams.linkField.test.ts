import { describe, expect, test, vi } from "vitest";
import type { FieldModelAttrs } from "../../FieldModel";
import { makeMockSample } from "./mocking";

vi.mock("../../../use-stores", () => () => {});
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    unitStore: {
      getUnit: () => ({ label: "ml" }),
    },
  }),
}));

const linkField = (id: number, link: FieldModelAttrs["link"]): FieldModelAttrs => ({
  id,
  name: `Link ${id}`,
  type: "link",
  content: null,
  selectedOptions: null,
  columnIndex: id,
  attachment: null,
  mandatory: false,
  allowedRelationTypes: [],
  link,
});

describe("sampleCreationParams with Link fields", () => {
  test("leaves out a filestore item, which a template's default link cannot hold, and keeps a Global ID", async () => {
    const sample = makeMockSample({
      fields: [
        linkField(1, {
          relationType: "IsSourceOf",
          targetGlobalId: null,
          versionPin: null,
          filestoreTarget: { filestoreId: 12, remotePath: "/XRD/run.dat" },
        }),
        linkField(2, { relationType: "References", targetGlobalId: "SA42", versionPin: null }),
      ],
    });

    const params = (await sample.sampleCreationParams(new Set())) as { fields: Array<{ link?: unknown }> };

    expect(params.fields[0].link).toBeNull();
    expect(params.fields[1].link).toEqual({ relationType: "References", targetGlobalId: "SA42", versionPin: null });
  });
});
