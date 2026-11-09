import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { escapeRegExp, sortBy } from "es-toolkit";
import type { OpenApiDocument } from "orval";
import * as v from "valibot";
import { dereferenceApiV2OpenApi } from "../../src/modules/common/api/v2/openApiReferences.ts";
import {
  type ApiV2MetadataChild,
  metadataAllowedFieldsSchema,
  metadataDocumentSchema,
  metadataFilterSchema,
  metadataLimitSchema,
  metadataOperationSchema,
  metadataReadSchema,
  metadataRowBoundsSchema,
  metadataRuntimeFieldsSchema,
  metadataSelectorsSchema,
  metadataSortSchema,
  metadataStructuredPropertySchema,
  metadataWhereSchema,
} from "../../src/modules/common/table-list/api-v2/metadataSchemas.ts";
import { objectInput, runtimeFieldCatalogRoute } from "../../src/modules/common/table-list/api-v2/schemaPrimitives.ts";
import { biomeFormat } from "./biome.ts";
import { type FilterCatalog, filterCatalog, filteredWhereOperations } from "./filters.ts";
import { componentRefName, parseOpenApiDocument } from "./openapi.ts";

/** Validates one section of the OpenAPI document, naming the section and each failing path in the error. */
function parseOpenApiSection<TSchema extends v.GenericSchema>(
  schema: TSchema,
  input: unknown,
  section: string,
): v.InferOutput<TSchema> {
  const result = v.safeParse(schema, input);
  if (result.success) return result.output;
  const issues = result.issues.map((issue) => {
    const path = issue.path?.map((item) => String(item.key)).join(".") ?? "";
    return path ? `${path}: ${issue.message}` : issue.message;
  });
  throw new Error(`OpenAPI ${section}: Validation failed: ${issues.join("; ")}`);
}

/** A structured field's children, in the shape of the table list's `FieldConfig`. */
function declaredChildren(children: readonly ApiV2MetadataChild[], parent: string): Record<string, unknown>[] {
  return children.map((child) => {
    const { name, type: schema, writeConstraints, openApi } = child;
    const path = `${parent}.${name}`;
    const common = {
      name,
      required: child.requiredOnCreate,
      nullable: child.nullable,
      readOnly: child.readOnly,
      ...(child.defaultValue == null ? {} : { defaultValue: child.defaultValue }),
      writeConstraints,
    };
    if (schema.jsonType === "array" || schema.jsonType === "object") {
      if (child.fields.length === 0) throw new Error(`OpenAPI ${path} child fields: at least one child is required`);
      return {
        ...common,
        type: schema.jsonType,
        fields: declaredChildren(child.fields, path),
        ...(schema.jsonType === "array" ? parseOpenApiSection(metadataRowBoundsSchema, writeConstraints, path) : {}),
      };
    }
    if (child.fields.length !== 0) throw new Error(`OpenAPI scalar field ${path} cannot declare child fields`);
    switch (schema.jsonType) {
      case "integer":
      case "number":
        return { ...common, type: "number" };
      case "boolean":
        return { ...common, type: "boolean" };
      case "string": {
        const options = writeConstraints.enum ?? schema.structure.enum;
        if (options !== undefined) return { ...common, type: "select", options };
        if ((openApi.format ?? schema.format) === "date-time") return { ...common, type: "dateTime" };
        const maximumLength = writeConstraints.maxLength ?? schema.maxLength;
        return { ...common, type: "text", ...(maximumLength == null ? {} : { maximumLength }) };
      }
      default:
        throw new Error(`OpenAPI ${path} has an unsupported field type`);
    }
  });
}

/** The Read component's properties that declare `x-rspace-fields`, keyed by property name. */
function structuredFields(properties: Record<string, Record<string, unknown>>) {
  return Object.fromEntries(
    Object.entries(properties)
      .filter(([, property]) => property["x-rspace-fields"] !== undefined)
      .map(([name, property]) => {
        const parsed = parseOpenApiSection(metadataStructuredPropertySchema, property, name);
        const type = parsed.type;
        if (type !== "array" && type !== "object")
          throw new Error(`OpenAPI ${name} has x-rspace-fields on a scalar field`);
        return [
          name,
          {
            type,
            fields: declaredChildren(parsed["x-rspace-fields"], name),
            nullable: Array.isArray(property.type) && property.type.includes("null"),
            readOnly: parsed.readOnly,
            ...(type === "array" ? parseOpenApiSection(metadataRowBoundsSchema, property, name) : {}),
          },
        ];
      }),
  );
}

function operationsOfKind(paths: Record<string, unknown>, kind: string): [string, Record<string, unknown>][] {
  return Object.entries(paths).flatMap(([path, item]) => {
    const operation = v.safeParse(v.object({ get: objectInput }), item);
    return operation.success && operation.output.get["x-rspace-operation"] === kind
      ? [[path, operation.output.get] as [string, Record<string, unknown>]]
      : [];
  });
}

/** Matches a catalog URL against a templated RUNTIME_FIELDS path such as `/api/v2/instruments/fields/{namespace}`. */
function routeMatches(template: string, catalog: string): boolean {
  const pattern = template
    .split(/\{[^/}]+\}/)
    .map(escapeRegExp)
    .join("[^/]+");
  return new RegExp(`^${pattern}$`).test(catalog);
}

function runtimeFieldNamespaces(declared: unknown, resourceName: string, runtimeRoutes: readonly string[]) {
  const descriptors = parseOpenApiSection(metadataRuntimeFieldsSchema, declared, `${resourceName} runtime fields`);
  for (const { namespace, catalog, via } of descriptors) {
    const parts = namespace.split(".");
    const catalogNamespace = parts.at(-1);
    if (catalogNamespace !== catalog.split("/").at(-1)) {
      throw new Error(`Runtime field namespace ${namespace} does not match its catalog`);
    }
    if (runtimeFieldCatalogRoute.exec(catalog)?.[2] !== catalogNamespace)
      throw new Error("Unsupported runtime field catalog route");
    if (!runtimeRoutes.some((route) => routeMatches(route, catalog))) {
      throw new Error(`Runtime field catalog ${catalog} is not a RUNTIME_FIELDS operation`);
    }
    if ((parts.length === 1 && via !== "") || (parts.length === 2 && via !== parts[0])) {
      throw new Error(`Runtime field namespace ${namespace} has an unsupported relationship source`);
    }
  }
  // `viaResource` is validated but not part of the table metadata.
  return descriptors.map(({ viaResource: _viaResource, ...namespace }) => namespace);
}

function collectionMetadata(
  root: v.InferOutput<typeof metadataDocumentSchema>,
  path: string,
  resourceName: string,
  runtimeRoutes: readonly string[],
  filter: FilterCatalog[string] | undefined,
) {
  const operation = parseOpenApiSection(
    metadataOperationSchema,
    root.paths[path],
    `${resourceName} list operation`,
  ).get;
  const parameter = (name: string) => {
    const found = operation.parameters.find((candidate) => candidate.name === name);
    if (!found) throw new Error(`OpenAPI list operation has no ${name} parameter`);
    return found;
  };
  const sort = parseOpenApiSection(
    metadataSortSchema,
    parameter("sort")["x-rspace-sort"],
    `${resourceName} sort metadata`,
  );
  for (const { field } of sort.default) {
    if (!sort.fields.includes(field))
      throw new Error(`OpenAPI ${resourceName} default sort field ${field} is not sortable`);
  }
  const whereParameter = parameter("where");
  if (filter === undefined) throw new Error(`OpenAPI ${resourceName} list operation has no filter`);
  const limits = parseOpenApiSection(
    metadataFilterSchema,
    whereParameter["x-rspace-filter"],
    `${resourceName} filter metadata`,
  );
  const selectors = parseOpenApiSection(metadataSelectorsSchema, filter, `${resourceName} filter selectors`);
  const where = parseOpenApiSection(metadataWhereSchema, whereParameter.schema, `${resourceName} where schema`);
  const limit = parseOpenApiSection(metadataLimitSchema, parameter("limit").schema, `${resourceName} limit schema`);
  const allowedFields = parseOpenApiSection(
    metadataAllowedFieldsSchema,
    parameter("fields")["x-rspace-allowed-fields"],
    `${resourceName} allowed fields`,
  );
  const fields = allowedFields[resourceName];
  if (fields === undefined) throw new Error(`OpenAPI has no allowed fields for ${resourceName}`);
  const item = operation.responses["200"].content["application/json"].schema.properties.docs.items;
  const component = componentRefName(item.$ref);
  const definition = component === undefined ? undefined : root.components.schemas[component];
  if (component === undefined || definition === undefined)
    throw new Error(`OpenAPI Read component ${component ?? item.$ref} was not found`);
  const read = parseOpenApiSection(metadataReadSchema, definition, `${component} Read component`);
  return {
    resourceName,
    fields,
    structuredFields: structuredFields(read.properties),
    sorting: { fields: sort.fields, default: sort.default, maximumFields: sort.maximumFields },
    filtering: {
      selectors,
      limits: { ...limits, maximumWhereLength: where.maxLength },
    },
    runtimeFields: runtimeFieldNamespaces(whereParameter["x-rspace-runtime-fields"], resourceName, runtimeRoutes),
    pagination: { defaultLimit: limit.default, maximumLimit: limit.maximum },
  };
}

export type TableListMetadataEntry = ReturnType<typeof collectionMetadata>;

/** The table-list metadata of every resource with a LIST operation, keyed by resource name. */
export function tableListMetadata(document: unknown): Record<string, TableListMetadataEntry> {
  // Selectors are classified once, by the filter-types generator, from the raw document.
  const catalog = filterCatalog(document as OpenApiDocument);
  const filters = filteredWhereOperations(document as OpenApiDocument);
  const root = parseOpenApiSection(metadataDocumentSchema, dereferenceApiV2OpenApi(document), "document");
  const runtimeRoutes = operationsOfKind(root.paths, "RUNTIME_FIELDS").map(([path]) => path);
  const entries = operationsOfKind(root.paths, "LIST").flatMap(([path, operation]) => {
    const tags = parseOpenApiSection(v.array(v.string()), operation.tags, `${path} tags`);
    const filter = filters.get(String(operation.operationId));
    return tags.map(
      (resourceName) =>
        [
          resourceName,
          collectionMetadata(
            root,
            path,
            resourceName,
            runtimeRoutes,
            filter === undefined ? undefined : catalog[filter],
          ),
        ] as const,
    );
  });
  return Object.fromEntries(sortBy(entries, [([name]) => name]));
}

export function generateTableMetadata(document: unknown): string {
  const source = `// Generated by scripts/api-v2-valibot/tableMetadata.ts. Do not edit.
/** Sorting, filtering, projection and runtime-field rules of each API v2 collection, read from the OpenAPI document. */
export const apiV2CollectionMetadata = ${JSON.stringify(tableListMetadata(document))} as const;
`;
  return biomeFormat(source, "tableListMetadata.ts");
}

if (import.meta.main) {
  const ui = resolve(import.meta.dirname, "../..");
  const document = parseOpenApiDocument(
    await readFile(resolve(ui, "src/modules/common/api/v2/openapi.generated.json"), "utf8"),
  );
  await writeFile(
    resolve(ui, "src/modules/common/api/v2/generated/tableListMetadata.ts"),
    generateTableMetadata(document),
  );
}
