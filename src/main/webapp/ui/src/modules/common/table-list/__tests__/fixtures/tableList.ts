import type * as v from "valibot";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import type { RuntimeFieldDefinition } from "../../api-v2/runtimeFieldCatalog";
import type { FilterState, PageState } from "../../types";
import type { testRecordSchema } from "./schema";

export type TestRecord = v.InferOutput<typeof testRecordSchema>;

export const config = resolveCollectionConfig<TestRecord>({
  slug: "records",
  idField: "id",
  labels: {
    singularKey: "tableList.examples.record",
    pluralKey: "tableList.examples.records",
    descriptionKey: "tableList.examples.description",
  },
  useAsTitle: "title",
  defaultColumns: ["title", "owner", "score", "enabled", "modifiedAt"],
  listSearchableFields: ["title", "owner"],
  pagination: { defaultLimit: 2, limits: [2, 5, 10] },
  defaultSort: [{ field: "modifiedAt", direction: "desc" }],
  fields: [
    { name: "id", labelKey: "tableList.examples.fields.id", type: "text", list: false },
    {
      name: "title",
      labelKey: "tableList.examples.fields.title",
      type: "text",
      list: { descriptionKey: "tableList.examples.fields.titleDescription" },
    },
    { name: "owner", labelKey: "tableList.examples.fields.owner", type: "text" },
    { name: "score", labelKey: "tableList.examples.fields.score", type: "number" },
    { name: "enabled", labelKey: "tableList.examples.fields.enabled", type: "boolean" },
    { name: "modifiedAt", labelKey: "tableList.examples.fields.modified", type: "dateTime" },
  ],
});

export const records: readonly TestRecord[] = [
  { id: "1", title: "Alpha", owner: "Ada", score: 8, enabled: true, modifiedAt: "2026-08-01T10:00:00Z" },
  { id: "2", title: "Beta", owner: "Grace", score: 4, enabled: false, modifiedAt: "2026-08-03T10:00:00Z" },
  { id: "3", title: "Gamma", owner: "Ada", score: 8, enabled: true, modifiedAt: "2026-08-02T10:00:00Z" },
];

export const emptyFilters: FilterState<TestRecord> = { search: "", expression: null };
export const firstPage: PageState = { pageIndex: 0, pageSize: 2 };

export function definition(overrides: Partial<RuntimeFieldDefinition> = {}): RuntimeFieldDefinition {
  return {
    id: "SF104",
    selector: "customFields.SF104",
    label: "Hazard class",
    type: "text",
    jsonType: "string",
    operators: ["==", "!=", "=in=", "=out=", "=contains=", "=like=", "=exists="],
    supportsWildcards: false,
    columnSelectable: true,
    sortable: false,
    source: { id: "IT9", label: "Cell line template" },
    options: [],
    ...overrides,
  };
}
