import { Link, linkOptions } from "@tanstack/react-router";
import { ChevronRightIcon, SettingsIcon, SlidersHorizontalIcon } from "lucide-react";
import { useTranslation } from "react-i18next";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/modules/common/ui/collapsible";
import {
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
} from "@/modules/common/ui/sidebar";

const items = () =>
  [
    {
      key: "preferences",
      icon: SlidersHorizontalIcon,
      link: <Link {...linkOptions({ to: "/booking/preferences" })} />,
    },
    {
      key: "administration",
      icon: SettingsIcon,
      children: [{ key: "settings", link: <Link {...linkOptions({ to: "/booking/config/settings" })} /> }],
    },
  ] as const;

/** Content for the shared AppShell sidebar. The shell owns the surrounding layout. */
export function BookingSidebar() {
  const { t } = useTranslation("booking");
  const { data: currentUser } = useCurrentUserQuery();
  const sidebarItems = items();
  const labels = {
    preferences: t("sidebar.preferences"),
    administration: t("sidebar.administration"),
    settings: t("sidebar.settings"),
  };
  const visibleItems = currentUser.hasSysAdminRole
    ? sidebarItems
    : sidebarItems.filter((item) => item.key !== "administration");

  return (
    <SidebarGroup>
      <SidebarGroupLabel>{t("sidebar.label")}</SidebarGroupLabel>
      <SidebarGroupContent>
        <SidebarMenu>
          {visibleItems.map((item) =>
            "children" in item ? (
              // shadcn's Base UI sidebar-menu-collapsible example, with the Collapsible rendered as the
              // <li> so the menu stays a valid ul > li list, and Base UI's data-open in place of data-state.
              <Collapsible key={item.key} className="group/collapsible" defaultOpen render={<SidebarMenuItem />}>
                <CollapsibleTrigger render={<SidebarMenuButton tooltip={labels[item.key]} />}>
                  <item.icon />
                  <span>{labels[item.key]}</span>
                  <ChevronRightIcon className="ml-auto transition-transform group-data-open/collapsible:rotate-90" />
                </CollapsibleTrigger>
                <CollapsibleContent>
                  <SidebarMenuSub>
                    {item.children.map((child) => (
                      <SidebarMenuSubItem key={child.key}>
                        {/* an <a> without href has no role, so unrouted sub-items render as buttons */}
                        <SidebarMenuSubButton render={"link" in child ? child.link : <button type="button" />}>
                          <span>{labels[child.key]}</span>
                        </SidebarMenuSubButton>
                      </SidebarMenuSubItem>
                    ))}
                  </SidebarMenuSub>
                </CollapsibleContent>
              </Collapsible>
            ) : (
              <SidebarMenuItem key={item.key}>
                <SidebarMenuButton
                  tooltip={labels[item.key]}
                  render={"link" in item ? item.link : <button type="button" />}
                >
                  <item.icon />
                  <span>{labels[item.key]}</span>
                </SidebarMenuButton>
              </SidebarMenuItem>
            ),
          )}
        </SidebarMenu>
      </SidebarGroupContent>
    </SidebarGroup>
  );
}
