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
export const httpMethods = ["get", "post", "put", "patch", "delete", "head", "options", "trace"] as const;
export type HttpMethod = (typeof httpMethods)[number];
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

/** Operations of a path item in document order, skipping path-level fields such as `parameters`. */
export function operationsOf(item: PathItem): [HttpMethod, OperationObject][] {
  return Object.entries(item).flatMap(([method, operation]) =>
    methods.has(method) ? [[method as HttpMethod, operation as OperationObject]] : [],
  );
}
