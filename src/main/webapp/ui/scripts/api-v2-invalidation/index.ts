import { createRequire } from "node:module";
import type { crossResourceInvalidation } from "../../src/modules/common/api/v2/crossResourceInvalidation";

// Resolve through Orval so tag buckets follow the pinned generator without another direct dependency.
const requireOrval = createRequire(createRequire(import.meta.url).resolve("orval"));
const { getTagKey }: { getTagKey: (tag?: string) => string } = requireOrval("@orval/core");

export interface Operation {
  operationId: string;
  tags?: readonly string[];
}
export interface Document {
  paths: Record<string, Record<string, unknown>>;
}
export interface InvalidationTarget {
  query: string;
  params?: string[];
  file?: string;
}
const methods = new Set(["get", "post", "put", "patch", "delete", "head", "options", "trace"]);
export function getOperations(document: Document) {
  return Object.entries(document.paths).flatMap(([path, item]) =>
    Object.entries(item).flatMap(([method, value]) => {
      if (!methods.has(method)) return [];
      const operation = value as Operation;
      if (!operation.operationId) throw new Error(`Missing operationId: ${method} ${path}`);
      return [{ ...operation, path, method, resource: path.replace(/^\/api\/v2\//, "").split("/")[0] }];
    }),
  );
}
export function queryFile(operation: Operation) {
  const tag = getTagKey(operation.tags?.[0]);
  return `./${tag}/${tag}.ts`;
}

export function buildInvalidationRules(document: Document, cross: typeof crossResourceInvalidation = []) {
  const operations = getOperations(document);
  const queries = operations.filter((operation) => operation.method === "get");
  const mutations = operations.filter((operation) => !["get", "head", "options"].includes(operation.method));
  for (const rule of cross) {
    for (const name of rule.onMutations) {
      if (!mutations.some((operation) => operation.operationId === name)) throw new Error(`Unknown mutation: ${name}`);
    }
  }
  return mutations.map((mutation) => {
    const root = `/api/v2/${mutation.resource}`;
    const targets: InvalidationTarget[] = queries
      .filter(
        (query) =>
          query.resource === mutation.resource &&
          (query.path === root ||
            query.path === `${root}/count` ||
            (mutation.path.includes("{id}") &&
              ["", "/access", "/audit"].some((suffix) => query.path === `${root}/{id}${suffix}`))),
      )
      .map((query) => ({ query: query.operationId, ...(query.path.includes("{id}") ? { params: ["id"] } : {}) }));
    for (const rule of cross) {
      if (rule.onMutations.includes(mutation.operationId)) {
        targets.push(
          ...rule.invalidates.map((target) => ({
            query: target.query,
            ...(target.params ? { params: [...target.params] } : {}),
          })),
        );
      }
    }
    const invalidates = targets.map((target) => {
      const query = queries.find((operation) => operation.operationId === target.query);
      if (!query) throw new Error(`Unknown query: ${target.query}`);
      return { ...target, ...(queryFile(query) !== queryFile(mutation) ? { file: queryFile(query) } : {}) };
    });
    return {
      onMutations: [mutation.operationId],
      invalidates: [...new Map(invalidates.map((target) => [JSON.stringify(target), target])).values()],
    };
  });
}
