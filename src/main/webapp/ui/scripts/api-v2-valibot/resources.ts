import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { sortBy, uniq } from "es-toolkit";
import type { OpenApiDocument } from "orval";
import { getTagKey } from "orval";
import { biomeFormat } from "./biome.ts";
import { filteredWhereOperations } from "./filters.ts";
import {
  componentRefName,
  isReference,
  operationsOf,
  orvalOperationName,
  orvalQueryKeyName,
  parseOpenApiDocument,
  responsesOf,
} from "./openapi.ts";

const slots = {
  LIST: "list",
  COUNT: "count",
  READ: "get",
  CREATE: "create",
  UPDATE: "update",
  DELETE: "delete",
  BULK_CREATE: "createMany",
  BULK_UPDATE: "updateMany",
  BULK_DELETE: "deleteMany",
  ACCESS_READ: "access",
  ACCESS_REPLACE: "replaceAccess",
  ACCESS_LEAVE: "leaveAccess",
  ACCESS_GRANTEE_SEARCH: "grantees",
  AUDIT_LIST: "audit",
  RUNTIME_FIELDS: "runtimeFields",
} as const;
type Kind = keyof typeof slots;
type Slot = (typeof slots)[Kind];
const slotOrder: readonly Slot[] = Object.values(slots);
const reads: ReadonlySet<Slot> = new Set(["list", "count", "get", "access", "grantees", "audit", "runtimeFields"]);

export interface ResourceOperation {
  id: string;
  file: string;
  read: boolean;
  /** RUNTIME_FIELDS only: the values of the namespace path parameter's enum. */
  namespaces?: string[];
}

interface ParameterObject {
  in?: string;
  $ref?: string;
  schema?: { enum?: unknown[] };
}

function runtimeFieldNamespaces(document: OpenApiDocument, parameters: unknown[], path: string): string[] {
  const components = (document.components?.parameters ?? {}) as Record<string, ParameterObject>;
  const pathParameters = (parameters as ParameterObject[])
    .map((entry) => (entry.$ref ? components[entry.$ref.split("/").at(-1) ?? ""] : entry))
    .filter((parameter) => parameter?.in === "path");
  const values = pathParameters.length === 1 ? pathParameters[0].schema?.enum : undefined;
  if (!values?.length || !values.every((value) => typeof value === "string"))
    throw new Error(`${path}: RUNTIME_FIELDS operation needs one path parameter with a string enum`);
  return values;
}

export function collectionOperations(document: OpenApiDocument): Map<string, Map<Slot, ResourceOperation>> {
  const resources = new Map<string, Map<Slot, ResourceOperation>>();
  for (const [path, item] of Object.entries(document.paths ?? {})) {
    for (const [, operation] of operationsOf(item)) {
      const kind = operation["x-rspace-operation"];
      if (kind === "CUSTOM") continue;
      if (typeof kind !== "string" || !Object.hasOwn(slots, kind))
        throw new Error(`${path}: ${kind == null ? "Untagged operation" : `Unknown operation kind ${kind}`}`);
      const slot = slots[kind as Kind];
      if (!operation.operationId || !operation.tags?.length) throw new Error(`Invalid resource operation: ${path}`);
      for (const resource of operation.tags) {
        const entries = resources.get(resource) ?? new Map<Slot, ResourceOperation>();
        if (entries.has(slot)) throw new Error(`Duplicate slot ${resource}.${slot}`);
        const tag = getTagKey(resource);
        entries.set(slot, {
          id: operation.operationId,
          file: `./${tag}/${tag}.ts`,
          read: reads.has(slot),
          ...(slot === "runtimeFields"
            ? { namespaces: runtimeFieldNamespaces(document, operation.parameters ?? [], path) }
            : {}),
        });
        resources.set(resource, entries);
      }
    }
  }
  return resources;
}

export async function generateResources(
  document: OpenApiDocument,
  readTarget: (file: string) => string | Promise<string>,
): Promise<string> {
  const resources = collectionOperations(document);
  const files = uniq(
    [...resources.values()].flatMap((entries) => [...entries.values()].map(({ file }) => file)),
  ).sort();
  const sources = new Map<string, string>();
  for (const file of files) sources.set(file, await readTarget(file));
  const imports = files.map((file, index) => `import * as resource${index} from ${JSON.stringify(file)};`);
  const filters = filteredWhereOperations(document);
  const checks: string[] = [];
  const entries = sortBy([...resources], [([name]) => name]).map(([name, operations]) => {
    const listId = operations.get("list")?.id;
    const filterResource = listId === undefined ? undefined : filters.get(listId);
    const namespaces = operations.get("runtimeFields")?.namespaces;
    const entry = `(typeof apiV2Resources)[${JSON.stringify(name)}]`;
    const assertions = [
      ...(filterResource ? [`Assert<Extends<${entry}["list"], ApiV2ListOperation<${entry}["filterResource"]>>>`] : []),
      ...(namespaces
        ? [
            `Assert<Extends<${entry}["runtimeFields"], ApiV2RuntimeFieldsOperation<${entry}["runtimeFieldNamespaces"][number]>>>`,
          ]
        : []),
    ];
    if (assertions.length) checks.push(`  ${JSON.stringify(name)}: [${assertions.join(", ")}];`);
    const metadata = [
      ...(filterResource ? [`    filterResource: ${JSON.stringify(filterResource)},`] : []),
      ...(namespaces ? [`    runtimeFieldNamespaces: ${JSON.stringify(namespaces)} as const,`] : []),
    ];
    const properties = sortBy([...operations], [([slot]) => slotOrder.indexOf(slot)]).map(
      ([slot, { id, file, read }]) => {
        const request = orvalOperationName(id);
        const key = orvalQueryKeyName(id);
        for (const target of read ? [request, key] : [request]) {
          if (!new RegExp(`export (?:const|function) ${target}\\b`).test(sources.get(file) ?? ""))
            throw new Error(`Missing import target ${target} in ${file}`);
        }
        const alias = `resource${files.indexOf(file)}`;
        return `    ${slot}: ${read ? `{ request: ${alias}.${request}, queryKey: ${alias}.${key} }` : `${alias}.${request}`},`;
      },
    );
    return `  ${JSON.stringify(name)}: {\n    name: ${JSON.stringify(name)},\n${[...metadata, ...properties].join("\n")}\n  } satisfies ApiV2ResourceEntry<${JSON.stringify(name)}, ApiV2OperationSet>,`;
  });
  const types = [
    "ApiV2OperationSet",
    "ApiV2ResourceEntry",
    ...(checks.some((check) => check.includes("ApiV2ListOperation")) ? ["ApiV2ListOperation"] : []),
    ...(checks.some((check) => check.includes("ApiV2RuntimeFieldsOperation")) ? ["ApiV2RuntimeFieldsOperation"] : []),
  ];
  // Each list and runtime-field catalog must accept the shared operation shape, so a divergent resource fails tsc here.
  const assertions = checks.length
    ? `\ntype Assert<T extends true> = T;\ntype Extends<A, B> = [A] extends [B] ? true : false;\nexport type ApiV2ResourceOperationChecks = {\n${checks.join("\n")}\n};\n`
    : "";
  const source = `// Generated by scripts/api-v2-valibot/resources.ts. Do not edit.\nimport type { ${types.join(", ")} } from "../apiV2Resources";\n${imports.join("\n")}\n\nexport const apiV2Resources = {\n${entries.join("\n")}\n};\n${assertions}`;
  return biomeFormat(source, "apiV2Resources.ts");
}

const GUARANTEED = "x-rspace-read-guaranteed";

type ComponentSchema = NonNullable<NonNullable<OpenApiDocument["components"]>["schemas"]>[string];
interface ReadSchema {
  name: string;
  schema: Exclude<ComponentSchema, boolean>;
}

/** The component schema each resource's READ operation returns, keyed like the registry and sorted by resource. */
export function readSchemas(document: OpenApiDocument): Map<string, ReadSchema> {
  const result = new Map<string, ReadSchema>();
  for (const [path, item] of Object.entries(document.paths ?? {})) {
    for (const [, operation] of operationsOf(item)) {
      if (operation["x-rspace-operation"] !== "READ") continue;
      const resource = operation.tags?.[0];
      const response = responsesOf(operation).find(([status]) => status === "200")?.[1];
      const readSchema = isReference(response) ? undefined : response?.content?.["application/json"]?.schema;
      const name = isReference(readSchema) ? componentRefName(readSchema.$ref) : undefined;
      const schema = name === undefined ? undefined : document.components?.schemas?.[name];
      if (!resource || name === undefined || typeof schema !== "object")
        throw new Error(`${path}: READ operation without a referenced read schema`);
      if (result.has(resource)) throw new Error(`Duplicate READ operation for ${resource}`);
      result.set(resource, { name, schema });
    }
  }
  return new Map(sortBy([...result], [([resource]) => resource]));
}

/**
 * The `*Read` properties each resource's readable document always contains when requested, keyed like the registry.
 * Only the `x-rspace-read-guaranteed` extension counts; access metadata is documentation, not proof.
 */
export function guaranteedReadFields(document: OpenApiDocument): Map<string, string[]> {
  return new Map(
    [...readSchemas(document)].map(([resource, { schema }]) => [
      resource,
      Object.entries("properties" in schema ? (schema.properties ?? {}) : {})
        .filter(
          ([, property]) => typeof property === "object" && GUARANTEED in property && property[GUARANTEED] === true,
        )
        .map(([field]) => field)
        .sort(),
    ]),
  );
}

/** A module apart from the registry, so importing the registry does not load every Valibot schema. */
export function generateReadSchemas(document: OpenApiDocument): string {
  const schemas = [...readSchemas(document)].map(([resource, { name }]) => [resource, `${name}Schema`] as const);
  const imports = schemas.length
    ? `import { ${uniq(schemas.map(([, schema]) => schema)).join(", ")} } from "./valibot/schemas";\n`
    : "";
  const source = `// Generated by scripts/api-v2-valibot/resources.ts. Do not edit.
${imports}
/** The Valibot schema of each resource's \`*Read\` document, keyed like the registry. */
export const apiV2ReadSchemas = {
${schemas.map(([resource, schema]) => `  ${JSON.stringify(resource)}: ${schema},`).join("\n")}
};
`;
  return biomeFormat(source, "apiV2ReadSchemas.ts");
}

export function generateGuaranteedReadFields(document: OpenApiDocument): string {
  const entries = [...guaranteedReadFields(document)].map(
    ([resource, fields]) =>
      `  ${JSON.stringify(resource)}: ${fields.length ? `new Set(${JSON.stringify(fields)} as const)` : "new Set<never>()"},`,
  );
  const source = `// Generated by scripts/api-v2-valibot/resources.ts. Do not edit.
/** Fields a readable document of each resource always contains when requested (\`${GUARANTEED}\`). */
export const apiV2GuaranteedReadFields = {
${entries.join("\n")}
};

export type ApiV2GuaranteedReadFields = {
  [R in keyof typeof apiV2GuaranteedReadFields]: (typeof apiV2GuaranteedReadFields)[R] extends ReadonlySet<infer F> ? F : never;
};
`;
  return biomeFormat(source, "guaranteedReadFields.ts");
}

export async function generateResourceFile(input: string, output: string): Promise<void> {
  const document = parseOpenApiDocument(await readFile(input, "utf8"));
  const source = await generateResources(document, (file) => readFile(resolve(output, file), "utf8"));
  await writeFile(resolve(output, "apiV2Resources.ts"), source);
  await writeFile(resolve(output, "guaranteedReadFields.ts"), generateGuaranteedReadFields(document));
  await writeFile(resolve(output, "apiV2ReadSchemas.ts"), generateReadSchemas(document));
}

if (import.meta.main) {
  const ui = resolve(import.meta.dirname, "../..");
  await generateResourceFile(
    resolve(ui, "src/modules/common/api/v2/openapi.generated.json"),
    resolve(ui, "src/modules/common/api/v2/generated"),
  );
}
