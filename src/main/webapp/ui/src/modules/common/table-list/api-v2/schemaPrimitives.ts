import * as v from "valibot";
import { apiV2WhereOperators } from "../../api/v2/apiV2Where.ts";

// Imports only valibot and node-safe relative modules, so the API v2 metadata generator can load it under plain `node`.

/** A plain object: not `null` and not an array. */
export const objectInput = v.custom<Record<string, unknown>>(
  (input) => typeof input === "object" && input !== null && !Array.isArray(input),
  "Expected an object",
);

/** An RSQL operator token that API v2 filter metadata may publish. */
export const filterOperatorSchema = v.picklist(Object.values(apiV2WhereOperators));

/** A runtime-field catalog route, `/api/v2/<resource>/fields/<namespace>`, capturing the resource and the namespace. */
export const runtimeFieldCatalogRoute = /^\/api\/v2\/([a-z][a-z0-9-]*)\/fields\/([A-Za-z][A-Za-z0-9_-]*)$/;
