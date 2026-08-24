import type { QueryFilters } from "@tanstack/react-query";

export const viewTransitionQueryMeta = { contributesToViewTransition: true } as const;

export const viewTransitionQueryFilters = {
  predicate: (query) => query.meta?.contributesToViewTransition === true,
} satisfies QueryFilters;
