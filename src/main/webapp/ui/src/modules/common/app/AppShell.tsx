import { useIsFetching } from "@tanstack/react-query";
import { CatchBoundary, HeadContent, Outlet, useMatches, useRouterState } from "@tanstack/react-router";
import { NuqsAdapter } from "nuqs/adapters/tanstack-router";
import * as React from "react";
import { useTranslation } from "react-i18next";
import FeatureFlagDevtoolsMount from "@/featureFlags/FeatureFlagDevtoolsMount";
import { viewTransitionQueryFilters } from "@/modules/common/queries/viewTransition";
import { UserSessionBootstrap } from "@/modules/common/stores/userSessionStore";
import {
  Sidebar,
  SidebarContent,
  SidebarInset,
  SidebarProvider,
  SidebarRail,
  SidebarTrigger,
} from "@/modules/common/ui/sidebar";
import { cn } from "@/modules/common/utils/cn";
import AuthenticatedAppBar, { PublicAppBar } from "./AppBar";
import type { AppBarConfig } from "./AppBar.types";

export type AppBarRouteContext = {
  appBar?: AppBarConfig | false;
  /** Rendered as the global sidebar next to the page content. */
  sidebar?: () => React.ReactNode;
};

export function getSidebarRenderer(matches: Array<{ context: unknown }>): (() => React.ReactNode) | undefined {
  for (let index = matches.length - 1; index >= 0; index -= 1) {
    const context = matches[index]?.context;
    if (typeof context !== "object" || context === null || !("sidebar" in context)) continue;

    const { sidebar } = context as AppBarRouteContext;
    if (sidebar !== undefined) return sidebar;
  }

  return undefined;
}

export function getAppBarConfig(matches: Array<{ context: unknown }>): AppBarConfig | false {
  for (let index = matches.length - 1; index >= 0; index -= 1) {
    const context = matches[index]?.context;
    if (typeof context !== "object" || context === null || !("appBar" in context)) continue;

    const { appBar } = context as AppBarRouteContext;
    if (appBar !== undefined) return appBar;
  }

  return { currentPage: "rspace" };
}

export function RouteTransitionIndicator() {
  const isRouteTransitioning = useRouterState({
    select: (state) => state.isLoading,
  });
  const viewTransitionQueries = useIsFetching(viewTransitionQueryFilters);
  const isTransitioning = isRouteTransitioning || viewTransitionQueries > 0;

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
  const renderSidebar = useMatches({
    select: (matches) => getSidebarRenderer(matches),
  });

  const appBarProps = appBarConfig !== false && {
    ...appBarConfig,
    renderHamburger:
      appBarConfig.renderHamburger ?? (renderSidebar === undefined ? undefined : () => <SidebarTrigger />),
  };

  const authenticatedAppBar = appBarProps !== false && (
    <React.Suspense fallback={<PublicAppBar {...appBarProps} />}>
      {/* inside the boundary so suspending queries use this fallback, not a blank page */}
      <UserSessionBootstrap />
      <AuthenticatedAppBar {...appBarProps} />
      <RouteTransitionIndicator />
    </React.Suspense>
  );

  return (
    // The app bar spans the full width, so the sidebar starts below it.
    <SidebarProvider
      key={renderSidebar === undefined ? "without-route-sidebar" : "with-route-sidebar"}
      className="flex-col"
      defaultOpen={Boolean(renderSidebar)}
      style={{ "--app-header-height": "calc(3rem + 3px)" } as React.CSSProperties}
    >
      <HeadContent />
      {appBarProps !== false && appBarProps.authenticated !== false && authenticatedAppBar}
      {appBarProps !== false && appBarProps.authenticated === false && (
        // Auth-optional page: authenticated bar for a logged-in user, public bar when auth queries reject.
        <CatchBoundary getResetKey={() => "public-app-bar"} errorComponent={() => <PublicAppBar {...appBarProps} />}>
          {authenticatedAppBar}
        </CatchBoundary>
      )}
      <div className="flex flex-1">
        {renderSidebar === undefined ? null : (
          <Sidebar className="top-(--app-header-height) h-[calc(100svh-var(--app-header-height))]">
            <SidebarContent>
              {/* local fallback: a sidebar's i18n namespace loads lazily and must not blank the page */}
              <React.Suspense fallback={null}>{renderSidebar()}</React.Suspense>
            </SidebarContent>
            <SidebarRail />
          </Sidebar>
        )}
        <SidebarInset>
          <NuqsAdapter>
            <React.Suspense fallback={<p>{t("loading")}</p>}>
              <Outlet />
            </React.Suspense>
          </NuqsAdapter>
        </SidebarInset>
      </div>
      {appBarProps !== false && appBarProps.authenticated !== false && (
        <React.Suspense fallback={null}>
          <FeatureFlagDevtoolsMount />
        </React.Suspense>
      )}
    </SidebarProvider>
  );
}
