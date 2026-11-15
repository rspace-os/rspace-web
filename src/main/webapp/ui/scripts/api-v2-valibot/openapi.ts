import { camel, pascal, sanitize, Verbs } from "orval";

/** The subset of OpenAPI 3.1 that the API v2 generation scripts read. */
export interface SchemaObject {
  $ref?: string;
  type?: string | string[];
  format?: string;
  nullable?: boolean;
  properties?: Record<string, SchemaObject>;
  items?: SchemaObject | boolean;
  additionalProperties?: SchemaObject | boolean;
  required?: string[];
  allOf?: SchemaObject[];
  anyOf?: SchemaObject[];
  oneOf?: SchemaObject[];
  [keyword: string]: unknown;
}
export interface MediaTypeObject {
  schema?: SchemaObject;
  [field: string]: unknown;
}
export type Content = Record<string, MediaTypeObject>;
export interface BodyObject {
  $ref?: string;
  content?: Content;
  [field: string]: unknown;
}
export type SecurityRequirement = Record<string, string[]>;
export interface OperationObject {
  operationId?: string;
  tags?: string[];
  security?: SecurityRequirement[];
  requestBody?: BodyObject;
  responses?: Record<string, BodyObject>;
  "x-rspace-operation"?: string | null;
  [field: string]: unknown;
}
/** The HTTP methods Orval generates operations for. */
export const httpMethods = Object.values(Verbs);
export type HttpMethod = (typeof Verbs)[keyof typeof Verbs];
export type PathItem = Partial<Record<HttpMethod, OperationObject>> & Record<string, unknown>;
export interface OpenApiDocument {
  security?: SecurityRequirement[];
  components?: {
    schemas?: Record<string, SchemaObject>;
    requestBodies?: Record<string, BodyObject>;
    responses?: Record<string, BodyObject>;
    [section: string]: unknown;
  };
  paths?: Record<string, PathItem>;
  [field: string]: unknown;
}

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

/** Operations of a path item in document order, skipping path-level fields such as `parameters`. */
export function operationsOf(item: PathItem): [HttpMethod, OperationObject][] {
  return Object.entries(item).flatMap(([method, operation]) =>
    methods.has(method) ? [[method as HttpMethod, operation as OperationObject]] : [],
  );
}
