import { useQuery } from "@tanstack/react-query";
import { freeze } from "immer";
import { getApiV2Config, getGetApiV2ConfigQueryOptions } from "@/modules/common/api/v2/generated/config/config";
import type { ApiV2Config } from "@/modules/common/api/v2/generated/models/apiV2Config";

export const DEFAULT_APP_CONFIG: ApiV2Config = freeze(
  {
    version: "",
    branding: { bannerImageUrl: "" },
    helpLinks: [],
    deploymentDescription: null,
    deploymentHelpEmail: null,
  },
  true,
);

export function getAppConfig() {
  return getApiV2Config();
}

export function useAppConfigQuery() {
  return useQuery(
    getGetApiV2ConfigQueryOptions({
      query: {
        placeholderData: DEFAULT_APP_CONFIG,
        staleTime: Infinity,
        gcTime: Infinity,
      },
    }),
  );
}
