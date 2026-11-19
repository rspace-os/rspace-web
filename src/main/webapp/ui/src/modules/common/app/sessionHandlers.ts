import type { AnyRouter } from "@tanstack/react-router";
import type { ApiV2SessionHandlers } from "@/modules/common/api/v2/tokenProvider";
import type { AppBarRouteContext } from "./AppShell";

/** Merges the route contexts the app shell reads, keeping loaded ancestors when a descendant's beforeLoad failed. */
export function resolveAppBarContext(matches: ReadonlyArray<{ context: AppBarRouteContext }>): AppBarRouteContext {
  return matches.reduce<AppBarRouteContext>(
    (context, match) => ({
      appBar: match.context.appBar ?? context.appBar,
      sidebar: match.context.sidebar ?? context.sidebar,
    }),
    {},
  );
}

/** A route needs a session unless its app bar is hidden or public; a route without an app bar context is public. */
export function requiresAuthentication({ appBar }: AppBarRouteContext): boolean {
  return appBar !== undefined && appBar !== false && appBar.authenticated !== false;
}

/**
 * The app shell's API v2 session handlers. An ended session navigates to `/login` only on routes that need
 * authentication; on auth-optional routes the caller's error reaches the page, which shows the public app bar.
 */
export function appShellSessionHandlers(router: Pick<AnyRouter, "state">): ApiV2SessionHandlers {
  return {
    onSessionEnded: () => {
      if (!requiresAuthentication(resolveAppBarContext(router.state.matches))) return;
      globalThis.location.assign("/login");
    },
    onIdentityChanged: () => globalThis.location.reload(),
  };
}
