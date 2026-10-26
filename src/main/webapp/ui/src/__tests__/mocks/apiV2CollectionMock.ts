import { parse } from "@rsql/parser";
import { HttpResponse } from "msw";
import calendarEventFilter from "./apiV2CalendarEventFilter.json";
import { apiV2OpenApi } from "./apiV2OpenApi";

type ExpressionNode = ReturnType<typeof parse>;

type ListParameter = {
  name: string;
  schema?: { minimum?: number; maximum?: number; default?: number };
  "x-rspace-filter"?: { selectors: Record<string, { operators: readonly string[] }> };
  "x-rspace-runtime-fields"?: readonly { namespace: string; filterable: boolean; columnSelectable: boolean }[];
  "x-rspace-sort"?: { fields: readonly string[]; maximumFields: number };
  "x-rspace-allowed-fields"?: Record<string, readonly string[]>;
};

function listParameters(resource: string): Map<string, ListParameter> {
  const paths = apiV2OpenApi.paths as Record<string, { get?: { parameters?: ListParameter[] } }>;
  const parameters = paths[`/api/v2/${resource}`]?.get?.parameters;
  if (!parameters) throw new Error(`The generated OpenAPI document has no list operation for ${resource}`);
  return new Map(parameters.map((parameter) => [parameter.name, parameter]));
}

function comparisons(node: ExpressionNode): { selector: string; operator: string }[] {
  return node.type === "COMPARISON"
    ? [{ selector: node.left.selector, operator: node.operator }]
    : [...comparisons(node.left), ...comparisons(node.right)];
}

function filterProblem(
  where: string | null,
  selectors: Record<string, { operators: readonly string[] }>,
  resolvedElsewhere: (selector: string) => boolean,
): string | null {
  if (!where) return null;
  let tree: ExpressionNode;
  try {
    tree = parse(where);
  } catch {
    return `where is not valid RSQL: ${where}`;
  }
  for (const { selector, operator } of comparisons(tree)) {
    const published = selectors[selector];
    if (!published) {
      if (resolvedElsewhere(selector)) continue;
      return `where uses the unpublished selector ${selector}`;
    }
    if (!published.operators.includes(operator)) return `where uses ${operator} on ${selector}`;
  }
  return null;
}

/** Why the backend would reject `where` for a collection, or null when its contract allows it. */
export function apiV2FilterProblem(resource: string, where: string | null): string | null {
  const filter = listParameters(resource).get("where");
  const runtime = (filter?.["x-rspace-runtime-fields"] ?? []).filter((namespace) => namespace.filterable);
  // Runtime-field operators come from their catalog definitions, not from this document.
  return filterProblem(where, filter?.["x-rspace-filter"]?.selectors ?? {}, (selector) =>
    runtime.some(({ namespace }) => selector.startsWith(`${namespace}.`)),
  );
}

/**
 * Why the Calendar endpoints would reject an event `where`. Fields reached through `target` or
 * `location` are resolved by the backend's registry, so only their selector roots are checked.
 */
export function calendarEventFilterProblem(where: string | null): string | null {
  return filterProblem(where, calendarEventFilter.selectors, (selector) => /^(target|location)\./.test(selector));
}

/**
 * Why the backend would reject this list request, or null when the generated contract allows it.
 * Checks the filter selectors and operators, sort fields, sparse field names and numeric bounds.
 */
export function apiV2CollectionRequestProblem(resource: string, url: URL): string | null {
  const parameters = listParameters(resource);
  for (const name of ["page", "limit", "depth"]) {
    const raw = url.searchParams.get(name);
    const schema = parameters.get(name)?.schema;
    if (raw === null || !schema) continue;
    const value = Number(raw);
    if (!Number.isInteger(value) || value < (schema.minimum ?? value) || value > (schema.maximum ?? value))
      return `${name}=${raw} is outside the published bounds`;
  }
  const filterProblem = apiV2FilterProblem(resource, url.searchParams.get("where"));
  if (filterProblem) return filterProblem;
  const sort = url.searchParams.get("sort");
  if (sort) {
    const published = parameters.get("sort")?.["x-rspace-sort"];
    const fields = sort.split(",").map((field) => field.replace(/^-/, ""));
    if (published && fields.length > published.maximumFields)
      return `sort names more than ${published.maximumFields} fields`;
    const unsortable = fields.find((field) => !published?.fields.includes(field));
    if (unsortable) return `sort uses the unsortable field ${unsortable}`;
  }
  // Column-selectable runtime namespaces project their fields through the sparse field list too.
  const projectable = (parameters.get("where")?.["x-rspace-runtime-fields"] ?? []).filter(
    (namespace) => namespace.columnSelectable,
  );
  for (const [key, value] of url.searchParams) {
    const sparse = /^(fields|exclude)\[(.+)\]$/.exec(key);
    if (!sparse) continue;
    const allowed = parameters.get(sparse[1])?.["x-rspace-allowed-fields"]?.[sparse[2]];
    if (!allowed) return `${key} names a resource this list cannot select fields from`;
    const unknown = value
      .split(",")
      .find(
        (field) => !allowed.includes(field) && !projectable.some(({ namespace }) => field.startsWith(`${namespace}.`)),
      );
    if (unknown) return `${key} names the unpublished field ${unknown}`;
  }
  return null;
}

/** The backend's list envelope arithmetic (`ApiV2ListResult.of`). */
export function apiV2ListResult(docs: readonly unknown[], totalDocs: number, limit: number, page: number) {
  const totalPages = limit > 0 && totalDocs > 0 ? Math.ceil(totalDocs / limit) : 0;
  const hasPrevPage = page > 1 && page <= totalPages;
  const hasNextPage = page < totalPages;
  return {
    docs,
    totalDocs,
    limit,
    page,
    pagingCounter: (page - 1) * limit + 1,
    totalPages,
    hasPrevPage,
    hasNextPage,
    prevPage: hasPrevPage ? page - 1 : null,
    nextPage: hasNextPage ? page + 1 : null,
  };
}

/** A problem response shaped like the backend's 400. */
export function badRequest(detail: string) {
  return HttpResponse.json(
    { type: "about:blank", title: "Bad Request", status: 400, detail },
    { status: 400, headers: { "Content-Type": "application/problem+json" } },
  );
}

/**
 * Answers a REST API v2 list request as the backend would: 400 when the generated contract rejects
 * it, otherwise the requested page of `matching`. Handlers still choose which rows match.
 */
export function apiV2CollectionResponse(resource: string, request: Request, matching: readonly unknown[]) {
  const url = new URL(request.url);
  const problem = apiV2CollectionRequestProblem(resource, url);
  if (problem) return badRequest(problem);
  const limit = Number(url.searchParams.get("limit") ?? listParameters(resource).get("limit")?.schema?.default ?? 20);
  const page = Number(url.searchParams.get("page") ?? 1);
  const start = (page - 1) * limit;
  return HttpResponse.json(apiV2ListResult(matching.slice(start, start + limit), matching.length, limit, page));
}
