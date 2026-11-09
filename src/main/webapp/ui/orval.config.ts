import { readFileSync } from "node:fs";
import { defineConfig } from "orval";
import { buildInvalidationRules, type Document, getOperations } from "./scripts/api-v2-invalidation";
import { crossResourceInvalidation } from "./src/modules/common/api/v2/crossResourceInvalidation";

const spec: Document = JSON.parse(
  readFileSync(new URL("./src/modules/common/api/v2/openapi.generated.json", import.meta.url), "utf8"),
);
// Historical names are harmless at later layers: only operations present in the spec become overrides.
const headersOperations = new Set([
  "getMyBookingNotificationSubscription",
  "replaceMyBookingNotificationSubscription",
  "createOrReplaceBookingCalendarSubscription",
  "createOrReplaceUserBookingCalendarSubscription",
  "getBookingCalendarSubscription",
  "createBookingCalendarSubscription",
  "rotateBookingCalendarSubscription",
  "getUserBookingCalendarSubscription",
  "createUserBookingCalendarSubscription",
  "rotateUserBookingCalendarSubscription",
  "getAdminBookingSettings",
  "patchAdminBookingSettings",
]);
const operations = Object.fromEntries(
  getOperations(spec).map(({ operationId }) => [
    operationId,
    {
      requestOptions: { operationId },
      ...(headersOperations.has(operationId) || operationId === "downloadBookingCalendarFile"
        ? {
            mutator: {
              path: "./src/modules/common/api/v2/apiV2HeadersFetch.ts",
              name: operationId === "downloadBookingCalendarFile" ? "apiV2BlobFetch" : "apiV2HeadersFetch",
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
