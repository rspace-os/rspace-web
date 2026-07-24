import { HelpCircleIcon } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { Menu, MenuContent, MenuItem, MenuLinkItem, MenuTrigger } from "@/modules/common/ui/menu";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { useLighthouseSdk } from "./lighthouse";
import type { AppConfig } from "./queries/config";
import { useLivechatPropertiesQuery } from "./queries/livechatProperties";

export default function HelpMenu({ helpLinks }: { helpLinks: AppConfig["helpLinks"] }) {
  const { t } = useTranslation("common");
  const { data: livechatProperties } = useLivechatPropertiesQuery();
  const { lighthouseReady, showLighthouse } = useLighthouseSdk(livechatProperties);
  const hasExtraHelpLinks = helpLinks.length > 0;

  if (!hasExtraHelpLinks) {
    return (
      <Tooltip>
        <TooltipTrigger
          render={
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label={t("helpDocs.openHelp")}
              disabled={!lighthouseReady}
              onClick={showLighthouse}
            />
          }
        >
          <HelpCircleIcon />
        </TooltipTrigger>
        <TooltipContent>{t("helpDocs.openHelp")}</TooltipContent>
      </Tooltip>
    );
  }

  return (
    <Menu>
      <Tooltip>
        <TooltipTrigger
          render={
            <MenuTrigger
              className={buttonVariants({ variant: "ghost", size: "icon-sm" })}
              aria-label={t("helpDocs.openHelp")}
            />
          }
        >
          <HelpCircleIcon />
        </TooltipTrigger>
        <TooltipContent>{t("helpDocs.openHelp")}</TooltipContent>
      </Tooltip>
      <MenuContent>
        {helpLinks.map(({ label, url }) => (
          <MenuLinkItem key={`${label}-${url}`} href={url} rel="noreferrer">
            {label}
          </MenuLinkItem>
        ))}
        <MenuItem
          className="block w-full rounded-sm px-2 py-2 text-left text-sm hover:bg-muted"
          onClick={showLighthouse}
        >
          {t("helpDocs.documentation")}
        </MenuItem>
      </MenuContent>
    </Menu>
  );
}
