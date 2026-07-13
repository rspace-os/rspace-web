export type AppBarPage =
  | "workspace"
  | "gallery"
  | "inventory"
  | "system"
  | "myRSpace"
  | "aboutRSpace"
  | "booking"
  | "rspace";

import type { MakeRouteMatchUnion } from "@tanstack/react-router";
import type { ReactNode } from "react";

export type AppBarConfig = {
  currentPage: AppBarPage;
  /**
   * false = auth-optional page (publicly reachable): show the authenticated app
   * bar when the user is logged in, the public bar otherwise. Default (true /
   * unset) assumes an authenticated-only page.
   */
  authenticated?: boolean;
  renderHamburger?: () => ReactNode;
};

export type NavItem = {
  id: AppBarPage;
  label: string;
  href: string;
  routerTo?: MakeRouteMatchUnion["fullPath"];
  isVisible: boolean;
  description?: string;
  icon?: ReactNode;
  iconClassName?: string;
};
