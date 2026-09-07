import { Link } from "@tanstack/react-router";
import { useTranslation } from "react-i18next";
import type { AppBarPage, NavItem } from "./AppBar.types";

export default function MainNavigation({ currentPage, navItems }: { currentPage: AppBarPage; navItems: NavItem[] }) {
  const { t } = useTranslation("common");
  return (
    <nav className="hidden items-center gap-1 md:flex" aria-label={t("appBar.mainLinks")}>
      {navItems
        .filter((item) => item.isVisible)
        .map((item) => (
          <Link
            key={item.id}
            to={item.routerTo ?? item.href}
            reloadDocument={!item.routerTo}
            viewTransition={Boolean(item.routerTo)}
            aria-current={currentPage === item.id ? "page" : undefined}
            className="rounded-sm px-2 py-1 text-sm text-muted-foreground hover:text-foreground aria-[current=page]:font-medium aria-[current=page]:text-foreground"
          >
            {item.label}
          </Link>
        ))}
    </nav>
  );
}
