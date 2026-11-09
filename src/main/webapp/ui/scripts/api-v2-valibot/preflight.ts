import type { OpenApiDocument, SchemaObject } from "./openapi.ts";

const annotations: ReadonlySet<string> = new Set([
  "description",
  "title",
  "example",
  "examples",
  "default",
  "deprecated",
  "readOnly",
  "writeOnly",
]);
const keywords: ReadonlySet<string> = new Set([
  "$ref",
  "type",
  "const",
  "enum",
  "format",
  "allOf",
  "anyOf",
  "oneOf",
  "properties",
  "additionalProperties",
  "required",
  "items",
  "minLength",
  "maxLength",
  "pattern",
  "minimum",
  "maximum",
  "exclusiveMinimum",
  "minItems",
  "maxItems",
]);
const formats: ReadonlySet<string> = new Set([
  "date-time",
  "date",
  "email",
  "idn-email",
  "uri",
  "uri-reference",
  "binary",
  "int32",
  "int64",
]);

/** Reject schema drift before adapting native output. */
export function assertSupportedSpec(document: OpenApiDocument): void {
  const components = document.components?.schemas ?? {};
  const names = Object.keys(components).sort();
  const dependencies = new Map(names.map((name) => [name, new Set<string>()]));
  function fail(path: string, message: string): never {
    throw new Error(`${path}: ${message}`);
  }
  function refName(ref: unknown, path: string): string {
    const prefix = "#/components/schemas/";
    if (typeof ref !== "string" || !ref.startsWith(prefix)) fail(path, `Unsupported reference ${ref}`);
    const name = ref.slice(prefix.length).replaceAll("~1", "/").replaceAll("~0", "~");
    if (!Object.hasOwn(components, name)) fail(path, `Missing component ${name}`);
    return name;
  }
  function validate(schema: SchemaObject, path: string, owner?: string): void {
    if (!schema || typeof schema !== "object" || Array.isArray(schema)) fail(path, "Expected a schema object");
    for (const key of Object.keys(schema)) {
      if (
        !keywords.has(key) &&
        !annotations.has(key) &&
        !key.startsWith("x-rspace-") &&
        !(key === "nullable" && schema[key] === false)
      ) {
        fail(path, `Unsupported keyword ${key}`);
      }
    }
    if (schema.format && !formats.has(schema.format)) fail(path, `Unsupported format ${schema.format}`);
    if (schema.anyOf || schema.oneOf) {
      const composition = schema.anyOf ?? schema.oneOf ?? [];
      if (schema.anyOf && schema.oneOf) fail(path, "Mixed union compositions are unsupported");
      for (const key of Object.keys(schema)) {
        if (keywords.has(key) && !["anyOf", "oneOf", "type"].includes(key))
          fail(path, `Unsupported union sibling ${key}`);
      }
      if (schema.type && (!Array.isArray(schema.type) || !composition.every((part) => part.type)))
        fail(path, "Union type siblings must be redundant typed branches");
    }
    // A lone allOf member is an alias, such as a documented reference to a shared enum.
    if (schema.allOf && schema.allOf.length > 1) {
      for (const part of schema.allOf) {
        const target = part.$ref ? components[refName(part.$ref, path)] : part;
        if (target.type && target.type !== "object") fail(path, "allOf supports object members only");
      }
    }
    if (schema.$ref) {
      const name = refName(schema.$ref, path);
      if (owner) dependencies.get(owner)?.add(name);
      if (Object.keys(schema).some((key) => keywords.has(key) && key !== "$ref"))
        fail(path, "Validation siblings of $ref are unsupported");
    }
    for (const [key, child] of Object.entries(schema.properties ?? {}))
      validate(child, `${path}.properties.${key}`, owner);
    for (const key of ["items", "additionalProperties"] as const) {
      const child = schema[key];
      if (child !== undefined && typeof child !== "boolean") validate(child, `${path}.${key}`, owner);
    }
    for (const key of ["allOf", "anyOf", "oneOf"] as const) {
      for (const [i, child] of (schema[key] ?? []).entries()) validate(child, `${path}.${key}[${i}]`, owner);
    }
  }
  for (const name of names) {
    if (!/^[A-Za-z_$][\w$]*$/.test(name)) fail(name, "Component name is not a TypeScript identifier");
    validate(components[name], `components.schemas.${name}`, name);
  }
  // Inline schemas on errors, headers and non-JSON media must also stay in the subset.
  function validateInline(value: unknown, path: string): void {
    if (!value || typeof value !== "object") return;
    for (const [key, child] of Object.entries(value)) {
      if (annotations.has(key) || key.startsWith("x-")) continue;
      if (key === "schema") validate(child as SchemaObject, `${path}.schema`);
      else if (!(path === "document.components" && key === "schemas")) validateInline(child, `${path}.${key}`);
    }
  }
  validateInline(document, "document");
}
