import { useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import * as React from "react";
import * as v from "valibot";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";

const CurrentUserOrcidSchema = v.object({
  available: v.boolean(),
  id: v.nullable(v.string()),
});

const CurrentUserCapabilitiesSchema = v.object({
  canUseInventory: v.boolean(),
  canPublish: v.boolean(),
  canViewSystem: v.boolean(),
});

const CurrentUserSessionSchema = v.object({
  operatedAs: v.boolean(),
  lastSession: v.nullable(v.pipe(v.string(), v.isoTimestamp())),
});

const CurrentUserResponseSchema = v.object({
  id: v.number(),
  username: v.string(),
  email: v.string(),
  firstName: v.string(),
  lastName: v.string(),
  homeFolderId: v.nullable(v.number()),
  workbenchId: v.nullable(v.number()),
  hasPiRole: v.boolean(),
  hasSysAdminRole: v.boolean(),
  profileImageUrl: v.nullable(v.string()),
  orcid: CurrentUserOrcidSchema,
  capabilities: CurrentUserCapabilitiesSchema,
  session: CurrentUserSessionSchema,
});

export type CurrentUser = v.InferOutput<typeof CurrentUserResponseSchema>;
export type CurrentUserOrcid = v.InferOutput<typeof CurrentUserOrcidSchema>;
export type CurrentUserCapabilities = v.InferOutput<typeof CurrentUserCapabilitiesSchema>;
export type CurrentUserSession = v.InferOutput<typeof CurrentUserSessionSchema>;

export const currentUserQueryKeys = {
  all: ["rspace.api.v2.users"] as const,
  me: () => [...currentUserQueryKeys.all, "me"] as const,
};

export function parseCurrentUserResponse(data: unknown) {
  return parseOrThrow(CurrentUserResponseSchema, data);
}

export async function getCurrentUser(token: string): Promise<CurrentUser> {
  const response = await fetch("/api/v2/users/me", {
    method: "GET",
    headers: {
      Authorization: `Bearer ${token}`,
      "X-Requested-With": "XMLHttpRequest",
    },
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch current user: ${response.status} ${response.statusText}`.trim());
  }

  const data: unknown = await response.json();
  return parseCurrentUserResponse(data);
}

export function useCurrentUserQuery() {
  const { data: token } = useOauthTokenQuery();
  return useSuspenseQuery({
    queryKey: currentUserQueryKeys.me(),
    queryFn: () => getCurrentUser(token),
    staleTime: Infinity,
    gcTime: Infinity,
  });
}

export function useCurrentUserEventSync() {
  const queryClient = useQueryClient();

  React.useEffect(() => {
    const invalidateCurrentUser = () => {
      void queryClient.invalidateQueries({ queryKey: currentUserQueryKeys.me() });
    };

    window.addEventListener("USER_RENAME", invalidateCurrentUser);
    window.addEventListener("USER_SET_ORCID", invalidateCurrentUser);
    window.addEventListener("USER_SET_EMAIL", invalidateCurrentUser);

    return () => {
      window.removeEventListener("USER_RENAME", invalidateCurrentUser);
      window.removeEventListener("USER_SET_ORCID", invalidateCurrentUser);
      window.removeEventListener("USER_SET_EMAIL", invalidateCurrentUser);
    };
  }, [queryClient]);
}
