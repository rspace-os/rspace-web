import document from "./apiV2OpenApi.json";

/**
 * The REST API v2 OpenAPI document the backend generates. `ApiV2OpenApiFixtureTest` fails when
 * the JSON falls behind the resource definitions and prints the command that regenerates it.
 */
export const apiV2OpenApi = document;
