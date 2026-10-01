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
  /** false = auth-optional page: authenticated bar if logged in, else public bar. Default true = authenticated-only. */
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
