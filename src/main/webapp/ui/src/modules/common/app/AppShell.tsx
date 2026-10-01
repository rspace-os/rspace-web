import { useIsFetching } from "@tanstack/react-query";
import {
  CatchBoundary,
  HeadContent,
  Navigate,
  Outlet,
  useMatches,
  useRouter,
  useRouterState,
} from "@tanstack/react-router";
import { NuqsAdapter } from "nuqs/adapters/tanstack-router";
import * as React from "react";
import { useTranslation } from "react-i18next";
import FeatureFlagDevtoolsMount from "@/featureFlags/FeatureFlagDevtoolsMount";
import { useFeatureFlags } from "@/featureFlags/queries";
import { navigationProgressQueryFilters } from "@/modules/common/queries/navigationProgress";
import { UserSessionBootstrap } from "@/modules/common/stores/userSessionStore";
import {
  Sidebar,
  SidebarContent,
  SidebarInset,
  SidebarProvider,
  SidebarRail,
  SidebarTrigger,
  useSidebar,
} from "@/modules/common/ui/sidebar";
import { cn } from "@/modules/common/utils/cn";
import AuthenticatedAppBar, { PublicAppBar } from "./AppBar";
import type { AppBarConfig } from "./AppBar.types";

export type AppBarRouteContext = {
  appBar?: AppBarConfig | false;
  /** Rendered as the global sidebar next to the page content. */
  sidebar?: () => React.ReactNode;
};

export function RouteTransitionIndicator() {
  const isRouteTransitioning = useRouterState({
    select: (state) => state.isLoading,
  });
  const navigationProgressQueries = useIsFetching(navigationProgressQueryFilters);
  const isTransitioning = isRouteTransitioning || navigationProgressQueries > 0;

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

/** On mobile the sidebar is a modal sheet covering the page, so hide it once a navigation starts. */
function CloseMobileSidebarOnNavigation() {
  const router = useRouter();
  const { setOpenMobile } = useSidebar();
  React.useEffect(() => router.subscribe("onBeforeNavigate", () => setOpenMobile(false)), [router, setOpenMobile]);
  return null;
}

export default function AppShell() {
  const { t } = useTranslation("common");
  const context = useMatches({
    // A failed beforeLoad leaves descendant contexts empty, so retain the loaded ancestors.
    select: (matches) =>
      matches.reduce<AppBarRouteContext>(
        (context, match) => ({
          appBar: match.context.appBar ?? context.appBar,
          sidebar: match.context.sidebar ?? context.sidebar,
        }),
        {},
      ),
  });
  const appBarConfig: AppBarConfig | false = context.appBar ?? { currentPage: "rspace" };
  return appBarConfig !== false && appBarConfig.currentPage === "booking" ? (
    <React.Suspense fallback={<p role="status">{t("loading")}</p>}>
      <BookingAppShell appBarConfig={appBarConfig} renderSidebar={context.sidebar} />
    </React.Suspense>
  ) : (
    <LoadedAppShell appBarConfig={appBarConfig} renderSidebar={context.sidebar} />
  );
}

type LoadedAppShellProps = {
  appBarConfig: AppBarConfig | false;
  renderSidebar: AppBarRouteContext["sidebar"];
};

function BookingAppShell(props: LoadedAppShellProps) {
  const { t } = useTranslation("common");
  const flags = useFeatureFlags();
  // Gate the entire shell: the Booking sidebar also fetches booking preferences.
  if (flags.isPlaceholderData && !flags.isError) return <p role="status">{t("loading")}</p>;
  if (!flags.data?.flags.bookingEnabled.value || flags.isError) {
    return <Navigate from="/booking" href="/workspace" reloadDocument replace />;
  }
  return <LoadedAppShell {...props} />;
}

function LoadedAppShell({ appBarConfig, renderSidebar }: LoadedAppShellProps) {
  const { t } = useTranslation("common");

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
      <CloseMobileSidebarOnNavigation />
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
              <nav>
                <React.Suspense fallback={null}>{renderSidebar()}</React.Suspense>
              </nav>
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
      <FeatureFlagDevtoolsMount />
    </SidebarProvider>
  );
}
