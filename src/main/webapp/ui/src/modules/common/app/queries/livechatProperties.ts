import { useQuery } from "@tanstack/react-query";
import * as v from "valibot";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";

const LivechatPropertiesSchema = v.object({
  livechatEnabled: v.boolean(),
  livechatServerKey: v.optional(v.string()),
  currentUser: v.string(),
});

export type LivechatProperties = v.InferOutput<typeof LivechatPropertiesSchema>;

export const livechatPropertiesQueryKeys = {
  all: ["rspace.common.livechatProperties"] as const,
  detail: () => [...livechatPropertiesQueryKeys.all, "detail"] as const,
};

export async function getLivechatProperties(): Promise<LivechatProperties> {
  const response = await fetch("/session/ajax/livechatProperties", {
    method: "GET",
    headers: {
      "X-Requested-With": "XMLHttpRequest",
    },
  });

  const data: unknown = await response.json();

  if (!response.ok) {
    throw new Error(`Failed to fetch livechat properties: ${response.statusText}`);
  }

  return parseOrThrow(LivechatPropertiesSchema, data);
}

export function useLivechatPropertiesQuery() {
  return useQuery({
    queryKey: livechatPropertiesQueryKeys.detail(),
    queryFn: getLivechatProperties,
    staleTime: Infinity,
    gcTime: Infinity,
  });
}
