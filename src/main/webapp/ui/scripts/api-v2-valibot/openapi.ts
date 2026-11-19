import type {
  OpenApiDocument,
  OpenApiMediaTypeObject,
  OpenApiOperationObject,
  OpenApiPathItemObject,
  OpenApiReferenceObject,
  OpenApiRequestBodyObject,
  OpenApiResponseObject,
  OpenApiSchemaObject,
} from "orval";
import { camel, pascal, sanitize, Verbs } from "orval";
import secureJsonParse from "secure-json-parse";

export type { OpenApiDocument };
/** An object schema. OpenAPI 3.1 also allows the boolean schemas `true` and `false`, which callers narrow first. */
export type SchemaObject = Exclude<OpenApiSchemaObject, boolean>;
export type ReferenceObject = OpenApiReferenceObject;
/** Whatever may stand where a schema is expected: an object schema, a boolean schema or a `$ref`. */
export type SchemaOrReference = OpenApiSchemaObject | ReferenceObject;
export type MediaTypeObject = OpenApiMediaTypeObject;
export type Content = Record<string, MediaTypeObject>;
export type BodyObject = OpenApiRequestBodyObject | OpenApiResponseObject;
export type OperationObject = OpenApiOperationObject;

/** Parses an OpenAPI document, rejecting prototype-poisoning keys (`__proto__` and `constructor.prototype`). */
export const parseOpenApiDocument = (text: string): OpenApiDocument =>
  secureJsonParse(text, null, { protoAction: "error", constructorAction: "error" });

/** True for a `$ref` object, the only OpenAPI value whose `$ref` is a string. */
export const isReference = (value: unknown): value is ReferenceObject =>
  typeof value === "object" && value !== null && "$ref" in value && typeof value.$ref === "string";

/** The HTTP methods Orval generates operations for. */
export const httpMethods = Object.values(Verbs);
export type HttpMethod = (typeof Verbs)[keyof typeof Verbs];
export type PathItem = OpenApiPathItemObject;
const methods: ReadonlySet<string> = new Set(httpMethods);

/** Orval's function name for an operation ID; generated request, hook and schema names derive from it. */
export const orvalOperationName = (operationId: string): string => sanitize(camel(operationId), { es5keyword: true });
/** Orval's exported Valibot or model name for a component or an operation name. */
export const orvalTypeName = (name: string): string => pascal(name);
/** Orval's query-key getter name for an operation ID. */
export const orvalQueryKeyName = (operationId: string): string =>
  camel(`get-${orvalOperationName(operationId)}-query-key`);
/** Orval's URL builder name for an operation ID. */
export const orvalUrlName = (operationId: string): string => camel(`get-${orvalOperationName(operationId)}-url`);

/** An operation's declared responses by status. */
export function responsesOf(operation: OperationObject): [string, OpenApiResponseObject | ReferenceObject][] {
  return Object.entries(operation.responses ?? {}) as [string, OpenApiResponseObject | ReferenceObject][];
}

/** A schema's `type` keyword; `undefined` for boolean schemas, references and untyped schemas. */
export const schemaType = (schema: SchemaOrReference | undefined): unknown =>
  typeof schema === "object" && schema !== null && "type" in schema ? schema.type : undefined;

/** Operations of a path item in document order, skipping path-level fields such as `parameters`. */
export function operationsOf(item: PathItem): [HttpMethod, OperationObject][] {
  return Object.entries(item).flatMap(([method, operation]) =>
    methods.has(method) ? [[method as HttpMethod, operation as OperationObject]] : [],
  );
}
