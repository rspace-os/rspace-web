import { Link } from "@tanstack/react-router";
import { ExternalLinkIcon, LogOutIcon, UserIcon } from "lucide-react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import type { CurrentUser } from "@/modules/common/queries/currentUser";
import { useUserSessionStore } from "@/modules/common/stores/userSessionStore";
import { Avatar, AvatarFallback, AvatarImage } from "@/modules/common/ui/avatar";
import { buttonVariants } from "@/modules/common/ui/button";
import { Menu, MenuContent, MenuItem, MenuLinkItem, MenuSeparator, MenuTrigger } from "@/modules/common/ui/menu";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { userInitials } from "@/modules/common/ui/user-badge";
import { clearStoredToken } from "@/modules/common/utils/auth";

function UserAvatar({
  operatedAs,
  profileImgSrc,
  fullName,
}: {
  operatedAs: boolean;
  profileImgSrc: string | null;
  fullName: string;
}) {
  return (
    <Avatar size="sm">
      {operatedAs ? (
        <AvatarFallback className="text-destructive">
          <UserIcon className="size-4" />
        </AvatarFallback>
      ) : (
        <>
          {profileImgSrc && <AvatarImage src={profileImgSrc} alt="" />}
          <AvatarFallback>{userInitials(fullName)}</AvatarFallback>
        </>
      )}
    </Avatar>
  );
}

export default function AccountMenu({
  currentUser,
  bannerImageUrl,
}: {
  currentUser: CurrentUser;
  bannerImageUrl: string;
}) {
  const { t } = useTranslation("common");
  const clearSessions = useUserSessionStore((state) => state.clearSessions);
  const fullName =
    [currentUser.firstName.trim(), currentUser.lastName.trim()].filter(Boolean).join(" ") || currentUser.username;
  const { operatedAs } = currentUser.session;

  const logoutHref = operatedAs ? "/logout/runAsRelease" : "/logout";
  const logoutLabel = operatedAs ? t("appBar.release") : t("appBar.logOut");

  const handleLogout = () => {
    clearSessions();
    clearStoredToken();
    if (!operatedAs && typeof window.gapi !== "undefined" && window.gapi.auth2) {
      const auth2 = window.gapi.auth2.getAuthInstance();
      if (auth2) void auth2.signOut();
    }
    window.location.href = logoutHref;
  };

  return (
    <Menu>
      <Tooltip>
        <TooltipTrigger
          render={
            <MenuTrigger
              className={buttonVariants({ variant: "ghost", size: "icon-sm" })}
              aria-label={t("appBar.accountMenu")}
            />
          }
        >
          <UserAvatar operatedAs={operatedAs} profileImgSrc={currentUser.profileImageUrl} fullName={fullName} />
        </TooltipTrigger>
        <TooltipContent>{t("appBar.sections.myRSpace.title")}</TooltipContent>
      </Tooltip>
      <MenuContent>
        <div className="flex gap-3 px-2 py-2 text-sm">
          <UserAvatar operatedAs={operatedAs} profileImgSrc={currentUser.profileImageUrl} fullName={fullName} />
          <div className="min-w-0">
            {operatedAs && <p className="font-medium">{t("appBar.operatingAs")}</p>}
            <p className="truncate font-medium">
              {t("appBar.userIdentity", { fullName, username: currentUser.username })}
            </p>
            <p className="truncate text-muted-foreground">{currentUser.email}</p>
            {currentUser.orcid.available && (
              <p className="mt-2 text-xs">
                {currentUser.orcid.id ? (
                  <span className="font-mono underline">{currentUser.orcid.id}</span>
                ) : (
                  <TransRichText i18nKey="common:appBar.orcidAdd" />
                )}
              </p>
            )}
          </div>
        </div>
        <MenuSeparator />
        <MenuLinkItem href={currentUser.hasPiRole ? "/groups/viewPIGroup" : "/userform"}>
          {t("appBar.sections.myRSpace.title")}
        </MenuLinkItem>
        <MenuLinkItem href="/dashboard">{t("appBar.messaging")}</MenuLinkItem>
        <MenuLinkItem href="/apps">{t("appBar.apps")}</MenuLinkItem>
        {currentUser.capabilities.canPublish && (
          <MenuLinkItem href="/public/publishedView/publishedDocuments" target="_blank">
            {t("appBar.published")} <ExternalLinkIcon className="size-3" />
          </MenuLinkItem>
        )}
        <MenuLinkItem render={<Link to="/about" />}>{t("appBar.aboutRSpace")}</MenuLinkItem>
        <MenuItem
          className="flex w-full items-center gap-2 rounded-sm px-2 py-2 text-left text-sm text-destructive hover:bg-muted"
          onClick={handleLogout}
        >
          <LogOutIcon className="size-4" />
          {logoutLabel}
        </MenuItem>
        {bannerImageUrl && (
          <div className="flex justify-end px-2 pt-2">
            <img className="max-h-10 max-w-32" src={bannerImageUrl} alt={t("appBar.brandingAlt")} />
          </div>
        )}
      </MenuContent>
    </Menu>
  );
}
