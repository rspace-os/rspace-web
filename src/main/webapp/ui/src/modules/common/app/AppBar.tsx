import {
  BellIcon,
  CalendarIcon,
  FlaskConicalIcon,
  ImageIcon,
  NotebookIcon,
  SettingsIcon,
  UserIcon,
} from "lucide-react";
import { useTranslation } from "react-i18next";
import RSpaceLogo from "@/assets/branding/rspace/logo.svg";
import RSpaceLogoTight from "@/assets/branding/rspace/logo-tight.svg";
import { FEATURE_FLAGS } from "@/featureFlags/generatedFeatureFlags";
import { useIsFeatureFlagEnabled } from "@/featureFlags/queries";
import { useCurrentUserEventSync, useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { buttonVariants } from "@/modules/common/ui/button";
import AccountMenu from "./AccountMenu";
import type { AppBarConfig, NavItem } from "./AppBar.types";
import HelpMenu from "./HelpMenu";
import MainNavigation from "./MainNavigation";
import MaintenanceNotice from "./MaintenanceNotice";
import MobileSectionSwitcher from "./MobileSectionSwitcher";
import { DEFAULT_APP_CONFIG, useAppConfigQuery } from "./queries/config";
import { useNextMaintenanceQuery } from "./queries/nextMaintenance";

export function PublicAppBar({ renderHamburger }: AppBarConfig) {
  const { t } = useTranslation("common");
  return (
    <header className="border-b bg-background">
      <div className="flex h-12 items-center gap-2 px-3">
        {renderHamburger?.()}
        <a href="/login" className="flex h-8 shrink-0 items-center">
          <picture>
            <source media="(max-width: 767px)" srcSet={RSpaceLogoTight} />
            <img src={RSpaceLogo} alt={t("helpDocs.rspaceAlt")} className="h-8" />
          </picture>
        </a>
        <div className="min-w-0 flex-1" />
        <a href="/login" className={buttonVariants({ variant: "outline", size: "sm" })}>
          {t("appBar.logIn")}
        </a>
      </div>
    </header>
  );
}

export default function AuthenticatedAppBar({ renderHamburger, currentPage }: AppBarConfig) {
  const { t } = useTranslation("common");
  useCurrentUserEventSync();
  const { data: currentUser } = useCurrentUserQuery();
  const { data: appConfig } = useAppConfigQuery();
  const { data: nextMaintenance } = useNextMaintenanceQuery();
  const config = appConfig ?? DEFAULT_APP_CONFIG;
  const showBooking = useIsFeatureFlagEnabled(FEATURE_FLAGS.bookingEnabled);
  const navItems: NavItem[] = [
    {
      id: "workspace",
      label: t("appBar.sections.workspace.title"),
      href: "/workspace",
      isVisible: true,
      description: t("appBar.sections.workspace.subheader"),
      icon: <NotebookIcon />,
      iconClassName: "text-cyan-500",
    },
    {
      id: "gallery",
      label: t("appBar.sections.gallery.title"),
      href: "/gallery",
      isVisible: true,
      description: t("appBar.sections.gallery.subheader"),
      icon: <ImageIcon />,
      iconClassName: "text-purple-500",
    },
    {
      id: "inventory",
      label: t("appBar.sections.inventory.title"),
      href: "/inventory",
      isVisible: currentUser.capabilities.canUseInventory,
      description: t("appBar.sections.inventory.subheader"),
      icon: <FlaskConicalIcon />,
      iconClassName: "text-green-500",
    },
    {
      id: "booking",
      label: t("appBar.sections.booking.title"),
      href: "/booking",
      routerTo: "/booking",
      isVisible: showBooking,
      description: t("appBar.sections.booking.subheader"),
      icon: <CalendarIcon />,
      iconClassName: "text-amber-500",
    },
    {
      id: "myRSpace",
      label: t("appBar.sections.myRSpace.title"),
      href: currentUser.hasPiRole ? "/groups/viewPIGroup" : "/userform",
      isVisible: true,
      description: t("appBar.sections.myRSpace.subheader"),
      icon: <UserIcon />,
      iconClassName: "text-slate-500",
    },
    {
      id: "system",
      label: t("appBar.sections.system.title"),
      href: "/system",
      isVisible: currentUser.capabilities.canViewSystem,
      description: t("appBar.sections.system.subheader"),
      icon: <SettingsIcon />,
      iconClassName: "text-blue-500",
    },
  ];

  return (
    <header className="border-b bg-background">
      <div className="flex h-12 items-center gap-2 px-3">
        {renderHamburger?.()}
        <a href="/workspace" className="flex h-8 shrink-0 items-center">
          <img src={RSpaceLogoTight} alt={t("helpDocs.rspaceAlt")} className="h-8 md:hidden" />
          {config.branding.bannerImageUrl ? (
            <img
              className="hidden max-h-8 max-w-32 md:block"
              src={config.branding.bannerImageUrl}
              alt={t("appBar.brandingAlt")}
            />
          ) : (
            <span className="hidden font-semibold md:inline">{t("helpDocs.rspaceAlt")}</span>
          )}
        </a>
        <MobileSectionSwitcher currentPage={currentPage} navItems={navItems} />
        <MainNavigation currentPage={currentPage} navItems={navItems} />
        <div className="min-w-0 flex-1" />
        {nextMaintenance && <MaintenanceNotice startDate={nextMaintenance.startDate} />}
        <a
          href="/dashboard"
          aria-label={t("appBar.notifications")}
          className={buttonVariants({ variant: "ghost", size: "icon-sm" })}
        >
          <BellIcon />
          <span className="sr-only">{t("appBar.notifications")}</span>
        </a>
        <AccountMenu currentUser={currentUser} bannerImageUrl={config.branding.bannerImageUrl} />
        <HelpMenu helpLinks={config.helpLinks} />
      </div>
    </header>
  );
}
