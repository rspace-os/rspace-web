import { Link } from "@tanstack/react-router";
import { ChevronDownIcon } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Button } from "@/modules/common/ui/button";
import { Menu, MenuContent, MenuLinkItem, MenuTrigger } from "@/modules/common/ui/menu";
import { cn } from "@/modules/common/utils/cn";
import type { AppBarPage, NavItem } from "./AppBar.types";
import { navLinkProps } from "./navigationLinkProps";

const itemClasses = "flex items-start gap-3 rounded-sm px-2 py-2 text-sm hover:bg-muted";

export default function MobileSectionSwitcher({
  currentPage,
  visibleNavItems,
}: {
  currentPage: AppBarPage;
  visibleNavItems: NavItem[];
}) {
  const { t } = useTranslation("common");
  const currentItem = visibleNavItems.find((item) => item.id === currentPage);

  return (
    <div className="md:hidden">
      <Menu>
        <MenuTrigger render={<Button variant="ghost" size="sm" />}>
          {currentItem ? currentItem.label : t("appBar.goTo")}
          <ChevronDownIcon />
        </MenuTrigger>
        <MenuContent align="start" aria-label={t("appBar.switchSection")}>
          {visibleNavItems.map((item) => (
            <MenuLinkItem
              key={item.id}
              render={<Link {...navLinkProps(item)} />}
              aria-current={currentPage === item.id ? "page" : undefined}
              className={itemClasses}
            >
              {item.icon && (
                <span className={cn("mt-0.5 shrink-0 [&_svg]:size-4", item.iconClassName ?? "text-muted-foreground")}>
                  {item.icon}
                </span>
              )}
              <span className="min-w-0">
                <span className={cn("block text-foreground", currentPage === item.id && "font-medium")}>
                  {item.label}
                </span>
                {item.description && <span className="block text-xs text-muted-foreground">{item.description}</span>}
              </span>
            </MenuLinkItem>
          ))}
        </MenuContent>
      </Menu>
    </div>
  );
}
