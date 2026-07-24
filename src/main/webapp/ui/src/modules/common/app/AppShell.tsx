import { CatchBoundary, HeadContent, Outlet, useMatches, useRouterState } from "@tanstack/react-router";
import * as React from "react";
import { useTranslation } from "react-i18next";
import FeatureFlagDevtoolsMount from "@/featureFlags/FeatureFlagDevtoolsMount";
import { UserSessionBootstrap } from "@/modules/common/stores/userSessionStore";
import { cn } from "@/modules/common/utils/cn";
import AuthenticatedAppBar, { PublicAppBar } from "./AppBar";
import type { AppBarConfig } from "./AppBar.types";

export type AppBarRouteContext = {
  appBar?: AppBarConfig | false;
};

export function getAppBarConfig(matches: Array<{ context: unknown }>): AppBarConfig | false {
  for (let index = matches.length - 1; index >= 0; index -= 1) {
    const context = matches[index]?.context;
    if (typeof context !== "object" || context === null || !("appBar" in context)) continue;

    const { appBar } = context as AppBarRouteContext;
    if (appBar !== undefined) return appBar;
  }

  return { currentPage: "rspace" };
}

function RouteTransitionIndicator() {
  const isTransitioning = useRouterState({
    select: (state) => state.isLoading,
  });

  return (
    <div className="h-0.5 bg-transparent" aria-hidden={!isTransitioning}>
      <div
        className={cn(
          "h-full bg-primary transition-[width,opacity] duration-300 ease-out",
          isTransitioning ? "w-full opacity-100" : "w-0 opacity-0",
        )}
      />
    </div>
  );
}

export default function AppShell() {
  const { t } = useTranslation("common");
  const appBarConfig = useMatches({
    select: (matches) => getAppBarConfig(matches),
  });

  const authenticatedAppBar = appBarConfig !== false && (
    <React.Suspense fallback={<PublicAppBar {...appBarConfig} />}>
      {/* inside the boundary so suspending queries use this fallback, not a blank page */}
      <UserSessionBootstrap />
      <AuthenticatedAppBar {...appBarConfig} />
      <RouteTransitionIndicator />
    </React.Suspense>
  );

  return (
    <>
      <HeadContent />
      {appBarConfig !== false && appBarConfig.authenticated !== false && authenticatedAppBar}
      {appBarConfig !== false && appBarConfig.authenticated === false && (
        // Auth-optional page: authenticated bar for a logged-in user, public bar when auth queries reject.
        <CatchBoundary getResetKey={() => "public-app-bar"} errorComponent={() => <PublicAppBar {...appBarConfig} />}>
          {authenticatedAppBar}
        </CatchBoundary>
      )}
      <React.Suspense fallback={<p>{t("loading")}</p>}>
        <Outlet />
      </React.Suspense>
      {appBarConfig !== false && appBarConfig.authenticated !== false && <FeatureFlagDevtoolsMount />}
    </>
  );
}
