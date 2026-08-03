import { Link } from "@tanstack/react-router";
import { ChevronDownIcon } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Button } from "@/modules/common/ui/button";
import { Menu, MenuContent, MenuLinkItem, MenuTrigger } from "@/modules/common/ui/menu";
import { cn } from "@/modules/common/utils/cn";
import type { AppBarPage, NavItem } from "./AppBar.types";

const itemClasses = "flex items-start gap-3 rounded-sm px-2 py-2 text-sm hover:bg-muted";

function SectionItemContent({ item, isCurrent }: { item: NavItem; isCurrent: boolean }) {
  return (
    <>
      {item.icon && (
        <span className={cn("mt-0.5 shrink-0 [&_svg]:size-4", item.iconClassName ?? "text-muted-foreground")}>
          {item.icon}
        </span>
      )}
      <span className="min-w-0">
        <span className={cn("block text-foreground", isCurrent && "font-medium")}>{item.label}</span>
        {item.description && <span className="block text-xs text-muted-foreground">{item.description}</span>}
      </span>
    </>
  );
}

export default function MobileSectionSwitcher({
  currentPage,
  navItems,
}: {
  currentPage: AppBarPage;
  navItems: NavItem[];
}) {
  const { t } = useTranslation("common");
  const visibleItems = navItems.filter((item) => item.isVisible);
  const currentItem = visibleItems.find((item) => item.id === currentPage);

  return (
    <div className="md:hidden">
      <Menu>
        <MenuTrigger render={<Button variant="ghost" size="sm" />}>
          {currentItem ? currentItem.label : t("appBar.goTo")}
          <ChevronDownIcon />
        </MenuTrigger>
        <MenuContent align="start" aria-label={t("appBar.switchSection")}>
          {visibleItems.map((item) =>
            item.routerTo ? (
              <MenuLinkItem
                key={item.id}
                render={<Link to={item.routerTo} viewTransition />}
                aria-current={currentPage === item.id ? "page" : undefined}
                className={itemClasses}
              >
                <SectionItemContent item={item} isCurrent={currentPage === item.id} />
              </MenuLinkItem>
            ) : (
              <MenuLinkItem
                key={item.id}
                href={item.href}
                aria-current={currentPage === item.id ? "page" : undefined}
                className={itemClasses}
              >
                <SectionItemContent item={item} isCurrent={currentPage === item.id} />
              </MenuLinkItem>
            ),
          )}
        </MenuContent>
      </Menu>
    </div>
  );
}
