import type { OpenApiDocument, OpenApiOperationObject } from "orval";
import { getTagKey } from "orval";
import type { crossResourceInvalidation } from "../../src/modules/common/api/v2/crossResourceInvalidation";
import { operationsOf } from "../api-v2-valibot/openapi.ts";

export interface InvalidationTarget {
  query: string;
  params?: string[];
  file?: string;
}
export function getOperations(document: OpenApiDocument) {
  return Object.entries(document.paths ?? {}).flatMap(([path, item]) =>
    operationsOf(item).map(([method, operation]) => {
      const { operationId } = operation;
      if (!operationId) throw new Error(`Missing operationId: ${method} ${path}`);
      return { ...operation, operationId, path, method, resource: path.replace(/^\/api\/v2\//, "").split("/")[0] };
    }),
  );
}
export function queryFile(operation: OpenApiOperationObject) {
  const tag = getTagKey(operation.tags?.[0]);
  return `./${tag}/${tag}.ts`;
}

// Pass this to a function-valued `mutationInvalidates` once Orval supports it:
// https://github.com/orval-labs/orval/issues/4552
export function buildInvalidationRules(document: OpenApiDocument, cross: typeof crossResourceInvalidation = []) {
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
