import { QueryErrorResetBoundary } from "@tanstack/react-query";
import { CatchBoundary } from "@tanstack/react-router";
import { type ReactNode, Suspense } from "react";

/**
 * Suspense plus an error boundary for `useSuspenseQuery` reads. `fallback` shows while the first read is pending;
 * `error` replaces the subtree when it fails, and its `retry` resets the failed queries and renders the subtree again.
 */
export function QueryBoundary({
  fallback,
  error,
  children,
}: {
  fallback: ReactNode;
  error: (failure: { error: unknown; retry: () => void }) => ReactNode;
  children: ReactNode;
}) {
  return (
    <QueryErrorResetBoundary>
      {({ reset: resetQueries }) => (
        <CatchBoundary
          getResetKey={() => "query"}
          errorComponent={({ error: caught, reset }) =>
            error({
              error: caught,
              retry: () => {
                resetQueries();
                reset();
              },
            })
          }
        >
          <Suspense fallback={fallback}>{children}</Suspense>
        </CatchBoundary>
      )}
    </QueryErrorResetBoundary>
  );
}
