import { useQuery } from "@tanstack/react-query";
import { freeze } from "immer";
import * as v from "valibot";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";

const AppConfigResponseSchema = v.object({
  version: v.string(),
  branding: v.object({
    bannerImageUrl: v.string(),
  }),
  helpLinks: v.array(
    v.object({
      label: v.string(),
      url: v.string(),
    }),
  ),
  deploymentDescription: v.nullable(v.string()),
  deploymentHelpEmail: v.nullable(v.pipe(v.string(), v.rfcEmail())),
});

export type AppConfig = v.InferOutput<typeof AppConfigResponseSchema>;

export const DEFAULT_APP_CONFIG: AppConfig = freeze(
  {
    version: "",
    branding: { bannerImageUrl: "" },
    helpLinks: [],
    deploymentDescription: null,
    deploymentHelpEmail: null,
  },
  true,
);

export const appConfigQueryKeys = {
  all: ["rspace.api.v2.config"] as const,
};

export async function getAppConfig(): Promise<AppConfig> {
  const response = await fetch("/api/v2/config", {
    method: "GET",
    headers: {
      "X-Requested-With": "XMLHttpRequest",
    },
  });

  if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);

  const data: unknown = await response.json();
  return parseOrThrow(AppConfigResponseSchema, data);
}

export function useAppConfigQuery() {
  return useQuery({
    queryKey: appConfigQueryKeys.all,
    queryFn: getAppConfig,
    placeholderData: DEFAULT_APP_CONFIG,
    staleTime: Infinity,
    gcTime: Infinity,
  });
}
