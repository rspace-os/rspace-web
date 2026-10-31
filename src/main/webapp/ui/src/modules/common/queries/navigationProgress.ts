import type { QueryFilters } from "@tanstack/react-query";

export const navigationProgressQueryMeta = { contributesToNavigationProgress: true } as const;

export const navigationProgressQueryFilters = {
  predicate: (query) => query.meta?.contributesToNavigationProgress === true,
} satisfies QueryFilters;
