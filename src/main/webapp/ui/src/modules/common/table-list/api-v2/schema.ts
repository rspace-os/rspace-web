import * as v from "valibot";
import type { apiV2ReadSchemas } from "@/modules/common/api/v2/generated/apiV2ReadSchemas";
import {
  ApiV2RuntimeFieldCatalogEntrySchema,
  ApiV2RuntimeFieldCatalogSchema,
} from "@/modules/common/api/v2/generated/valibot/schemas";
import { runtimeFieldValueTypes } from "@/modules/common/collection/runtimeFields";
import type { ApiV2RuntimeFieldNamespace } from "./metadata";
import { filterOperatorSchema, objectInput } from "./schemaPrimitives";

/**
 * A document schema must be an object schema: the adapter narrows it to the selected fields with
 * `v.pick` before it validates a response.
 */
export type ApiV2DocumentSchema<TDocument> = v.ObjectSchema<
  v.ObjectEntries & Partial<Record<Extract<keyof TDocument, string>, v.GenericSchema>>,
  undefined
>;

type ApiV2ReadSchemas = typeof apiV2ReadSchemas;

/** The generated `*Read` schema of a registry resource; `never` for a resource without one. */
export type ApiV2ReadSchemaOf<TEntry extends { readonly name: string }> = TEntry["name"] extends keyof ApiV2ReadSchemas
  ? ApiV2ReadSchemas[TEntry["name"]]
  : never;

type ApiV2ReadSchemaMapping<TDocument, TRead> = [TRead] extends [never]
  ? never
  : TRead extends v.GenericSchema
    ?
        | { documentSchema?: undefined; mapDocument: (document: v.InferOutput<TRead>) => TDocument }
        | (v.InferOutput<TRead> extends NoInfer<TDocument>
            ? { documentSchema?: undefined; mapDocument?: undefined }
            : never)
    : never;

/**
 * How a response document becomes a row. An explicit `documentSchema` always wins; a registry resource with a
 * generated read schema may omit it, and then omits `mapDocument` too when the read document is the row.
 */
export type ApiV2DocumentMapping<
  TDocument,
  TSchema extends ApiV2DocumentSchema<TDocument>,
  TEntry extends { readonly name: string } = { readonly name: string },
> =
  | { documentSchema: TSchema; mapDocument: (document: v.InferOutput<TSchema>) => TDocument }
  | { documentSchema: TSchema & v.GenericSchema<unknown, NoInfer<TDocument>>; mapDocument?: undefined }
  | ApiV2ReadSchemaMapping<TDocument, ApiV2ReadSchemaOf<TEntry>>;

export const runtimeFieldSchema = v.object({
  ...ApiV2RuntimeFieldCatalogEntrySchema.entries,
  // IDs become `namespace.id` selectors written unescaped into `where` and `fields[...]`.
  id: v.pipe(v.string(), v.regex(/^[A-Za-z0-9_-]+$/)),
  selector: v.pipe(v.string(), v.nonEmpty()),
  label: v.string(),
  type: v.picklist(runtimeFieldValueTypes),
  jsonType: v.picklist(["string", "number", "array"]),
  operators: v.array(filterOperatorSchema),
  supportsWildcards: v.boolean(),
  columnSelectable: v.boolean(),
  sortable: v.boolean(),
  source: v.object({ id: v.string(), label: v.string() }),
  options: v.array(v.string()),
});

export const catalogSchema = v.object({
  ...ApiV2RuntimeFieldCatalogSchema.entries,
  fields: v.array(runtimeFieldSchema),
  totalFields: v.optional(v.number()),
  hasMore: v.boolean(),
  page: v.number(),
  limit: v.number(),
});

/** Each requested namespace's first catalog page, from one runtime-field search. */
export const runtimeFieldSearchSchema = v.object({ catalogs: v.record(v.string(), catalogSchema) });

export const runtimeFieldValuesSchema = v.record(
  v.string(),
  v.nullable(v.union([v.string(), v.number(), v.array(v.string())])),
);

export function createApiV2ResponseSchema(
  documentSchema: v.ObjectSchema<v.ObjectEntries, undefined>,
  runtimeFields: readonly ApiV2RuntimeFieldNamespace[],
): v.ObjectSchema<v.ObjectEntries, undefined> {
  return runtimeFields.length === 0
    ? documentSchema
    : v.object({
        ...documentSchema.entries,
        ...Object.fromEntries(
          runtimeFields
            .filter((namespace) => namespace.responseField !== "")
            .map((namespace) => [namespace.responseField, v.optional(runtimeFieldValuesSchema)]),
        ),
      });
}

/** The keys a schema guarantees to keep, or `undefined` when it cannot accept the input in question at all. */
type Keys = ReadonlySet<string> | undefined;

const noKeys: ReadonlySet<string> = new Set();

/** Schema types that never accept an object, such as a reference's numeric ID. */
const nonObjectTypes = new Set([
  "bigint",
  "boolean",
  "date",
  "enum",
  "literal",
  "nan",
  "never",
  "null",
  "number",
  "picklist",
  "string",
  "symbol",
  "undefined",
  "void",
]);

/**
 * A pipeline whose later steps are schemas or transformations can drop keys its first schema declares. Every
 * transformation counts, including ones that keep keys such as `v.brand` and `v.readonly`.
 */
function changesOutput(schema: object): boolean {
  return (
    "pipe" in schema &&
    Array.isArray(schema.pipe) &&
    schema.pipe
      .slice(1)
      .some((item) => v.is(objectInput, item) && (item.kind === "schema" || item.kind === "transformation"))
  );
}

/**
 * Combines `keysOf` over the object schemas a schema accepts, looking through optional and nullable wrappers.
 * Valibot parses a union with its first matching arm, so a union keeps only the keys every arm that can accept the
 * input declares; an intersection keeps the keys of any member, and cannot accept the input when one member cannot.
 * A pipeline that may change its output guarantees no keys, and so does a schema the walk does not model (lazy, array,
 * record, unknown, rest), so a target field under one of those fails the adapter's declaration check.
 */
function acceptedKeys(schema: unknown, keysOf: (entries: Record<string, unknown>) => Keys): Keys {
  if (typeof schema !== "object" || schema === null) return undefined;
  if (changesOutput(schema)) return noKeys;
  if ("entries" in schema && v.is(objectInput, schema.entries)) return keysOf(schema.entries);
  if ("wrapped" in schema) return acceptedKeys(schema.wrapped, keysOf);
  if ("type" in schema && typeof schema.type === "string" && nonObjectTypes.has(schema.type)) return undefined;
  // Any other schema this walk does not model (lazy, array, record, unknown, rest) may accept and reshape the input.
  if (!("options" in schema) || !Array.isArray(schema.options)) return noKeys;
  const arms: Keys[] = schema.options.map((option) => acceptedKeys(option, keysOf));
  if ("type" in schema && schema.type === "intersect") {
    if (arms.some((keys) => keys === undefined)) return undefined;
    return new Set(arms.flatMap((keys) => [...(keys ?? [])]));
  }
  const accepting = arms.filter((keys) => keys !== undefined);
  if (accepting.length === 0) return undefined;
  return new Set([...accepting[0]].filter((key) => accepting.every((keys) => keys.has(key))));
}

/**
 * The keys a relationship schema guarantees to keep on its expanded `value` document. An arm without `value` strips
 * the whole document; an arm whose `value` is not a document (a reference ID) cannot accept an expanded one.
 */
export function expandedValueKeys(relationship: unknown): ReadonlySet<string> {
  return (
    acceptedKeys(relationship, (entries) =>
      "value" in entries ? acceptedKeys(entries.value, (value) => new Set(Object.keys(value))) : noKeys,
    ) ?? noKeys
  );
}
