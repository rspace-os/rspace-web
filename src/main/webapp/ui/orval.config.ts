import { readFileSync } from "node:fs";
import { defineConfig } from "orval";
import { buildInvalidationRules, type Document, getOperations } from "./scripts/api-v2-invalidation";
import { crossResourceInvalidation } from "./src/modules/common/api/v2/crossResourceInvalidation";

const spec: Document = JSON.parse(
  readFileSync(new URL("./src/modules/common/api/v2/openapi.generated.json", import.meta.url), "utf8"),
);
// Operations whose response headers (ETag, Location) or binary body need another apiV2Fetch.ts mutator.
const mutatorOverrides: Record<string, "apiV2HeadersFetch" | "apiV2BlobFetch"> = {};
const specOperationIds = new Set(getOperations(spec).map(({ operationId }) => operationId));
for (const operationId of Object.keys(mutatorOverrides)) {
  if (!specOperationIds.has(operationId)) throw new Error(`Mutator override for an unknown operation: ${operationId}`);
}
const operations = Object.fromEntries(
  getOperations(spec).map(({ operationId }) => [
    operationId,
    {
      requestOptions: { operationId },
      ...(Object.hasOwn(mutatorOverrides, operationId)
        ? {
            mutator: {
              path: "./src/modules/common/api/v2/apiV2Fetch.ts",
              name: mutatorOverrides[operationId],
              inferred: true,
            },
          }
        : {}),
    },
  ]),
);

export default defineConfig({
  api: {
    input: { target: "./src/modules/common/api/v2/openapi.generated.json" },
    output: {
      target: "./src/modules/common/api/v2/generated/api.ts",
      schemas: "./src/modules/common/api/v2/generated/models",
      headers: true,
      mode: "tags-split",
      client: "react-query",
      httpClient: "fetch",
      clean: true,
      override: {
        enumGenerationType: "union",
        mutator: { path: "./src/modules/common/api/v2/apiV2Fetch.ts", name: "apiV2Fetch" },
        fetch: { includeHttpResponseReturnType: false, arrayFormat: "repeat" },
        query: {
          version: 5,
          useSuspenseQuery: true,
          signal: true,
          useInvalidate: true,
          mutationInvalidates: buildInvalidationRules(spec, crossResourceInvalidation),
        },
        operations,
      },
    },
  },
  validation: {
    input: { target: "./src/modules/common/api/v2/openapi.generated.json" },
    output: {
      client: "valibot",
      mode: "single",
      target: "./src/modules/common/api/v2/generated/valibot/native.ts",
      override: { valibot: { generateReusableSchemas: true } },
    },
  },
});
