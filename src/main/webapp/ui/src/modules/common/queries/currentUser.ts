import { useQueryClient } from "@tanstack/react-query";
import * as React from "react";
import {
  getGetCurrentUserQueryOptions,
  invalidateGetCurrentUser,
  getCurrentUser as readCurrentUser,
  useGetCurrentUserSuspense,
} from "@/modules/common/api/v2/generated/users/users";

const currentUserEvents = ["USER_RENAME", "USER_SET_ORCID", "USER_SET_EMAIL"] as const;

export function getCurrentUser() {
  return readCurrentUser();
}

export function currentUserQueryOptions() {
  return getGetCurrentUserQueryOptions({ query: { staleTime: Infinity, gcTime: Infinity } });
}

export function useCurrentUserQuery() {
  return useGetCurrentUserSuspense({ query: { staleTime: Infinity, gcTime: Infinity } });
}

export function useCurrentUserEventSync() {
  const queryClient = useQueryClient();
  React.useEffect(() => {
    const invalidate = () => {
      void invalidateGetCurrentUser(queryClient);
    };
    for (const event of currentUserEvents) window.addEventListener(event, invalidate);
    return () => {
      for (const event of currentUserEvents) window.removeEventListener(event, invalidate);
    };
  }, [queryClient]);
}
