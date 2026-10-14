import { Link } from "@tanstack/react-router";
import { useTranslation } from "react-i18next";
import type { AppBarPage, NavItem } from "./AppBar.types";
import { navLinkProps } from "./navigationLinkProps";

export default function MainNavigation({
  currentPage,
  visibleNavItems,
}: {
  currentPage: AppBarPage;
  visibleNavItems: NavItem[];
}) {
  const { t } = useTranslation("common");
  return (
    <nav className="hidden items-center gap-1 md:flex" aria-label={t("appBar.mainLinks")}>
      {visibleNavItems.map((item) => (
        <Link
          key={item.id}
          {...navLinkProps(item)}
          aria-current={currentPage === item.id ? "page" : undefined}
          className="rounded-sm px-2 py-1 text-sm text-muted-foreground hover:text-foreground aria-[current=page]:font-medium aria-[current=page]:text-foreground"
        >
          {item.label}
        </Link>
      ))}
    </nav>
  );
}
