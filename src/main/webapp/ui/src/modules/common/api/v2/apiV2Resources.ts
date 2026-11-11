import type { QueryKey } from "@tanstack/react-query";
import { generatedCollectionResources } from "./generated/apiV2Resources";
import * as config from "./generated/config/config.ts";
import * as oauth from "./generated/oauth/oauth.ts";
import * as openapijson from "./generated/openapi/openapi.ts";

type ApiV2AsyncFunction = (...args: never[]) => Promise<unknown>;
type ApiV2KeyFunction = (...args: never[]) => QueryKey;

export interface ApiV2ReadOperation<
  TRequest extends ApiV2AsyncFunction = ApiV2AsyncFunction,
  TQueryKey extends ApiV2KeyFunction = ApiV2KeyFunction,
> {
  request: TRequest;
  queryKey: TQueryKey;
}

export interface ApiV2OperationSet {
  list?: ApiV2ReadOperation;
  count?: ApiV2ReadOperation;
  get?: ApiV2ReadOperation;
  create?: ApiV2AsyncFunction;
  runtimeFields?: ApiV2ReadOperation;
}

export type ApiV2ResourceEntry<TName extends string, TOperations extends ApiV2OperationSet> = {
  readonly name: TName;
} & TOperations;

const customResources = {
  config: {
    name: "config",
    get: { request: config.getApiV2Config, queryKey: config.getGetApiV2ConfigQueryKey },
  } satisfies ApiV2ResourceEntry<"config", ApiV2OperationSet>,
  oauth: { name: "oauth", create: oauth.createUiOAuthToken } satisfies ApiV2ResourceEntry<"oauth", ApiV2OperationSet>,
  "openapi.json": {
    name: "openapi.json",
    get: { request: openapijson.getApiV2OpenApiDocument, queryKey: openapijson.getGetApiV2OpenApiDocumentQueryKey },
  } satisfies ApiV2ResourceEntry<"openapi.json", ApiV2OperationSet>,
};

export const apiV2Resources = { ...generatedCollectionResources, ...customResources };
