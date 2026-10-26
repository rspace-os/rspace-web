import type { NavItem } from "./AppBar.types";

export function navLinkProps(item: NavItem) {
  return {
    to: item.routerTo ?? item.href,
    reloadDocument: !item.routerTo,
  };
}
