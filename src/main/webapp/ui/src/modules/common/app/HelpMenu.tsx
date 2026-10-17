import { HelpCircleIcon } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { Menu, MenuContent, MenuItem, MenuLinkItem, MenuTrigger } from "@/modules/common/ui/menu";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { type LivechatProperties, useLighthouseSdk } from "./lighthouse";
import type { AppConfig } from "./queries/config";

export default function HelpMenu({
  helpLinks,
  livechatProperties,
}: {
  helpLinks: AppConfig["helpLinks"];
  livechatProperties: LivechatProperties;
}) {
  const { t } = useTranslation("common");
  const { showLighthouse } = useLighthouseSdk(livechatProperties);
  const hasExtraHelpLinks = helpLinks.length > 0;

  const helpTrigger = (
    <Tooltip>
      <TooltipTrigger
        render={
          hasExtraHelpLinks ? (
            <MenuTrigger
              className={buttonVariants({ variant: "ghost", size: "icon-sm" })}
              aria-label={t("helpDocs.openHelp")}
            />
          ) : (
            <Button variant="ghost" size="icon-sm" aria-label={t("helpDocs.openHelp")} onClick={showLighthouse} />
          )
        }
      >
        <HelpCircleIcon />
      </TooltipTrigger>
      <TooltipContent>{t("helpDocs.openHelp")}</TooltipContent>
    </Tooltip>
  );

  if (!hasExtraHelpLinks) return helpTrigger;

  return (
    <Menu>
      {helpTrigger}
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
