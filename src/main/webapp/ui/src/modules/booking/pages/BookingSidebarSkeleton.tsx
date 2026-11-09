import { useTranslation } from "react-i18next";
import {
  SidebarGroup,
  SidebarGroupContent,
  SidebarMenu,
  SidebarMenuItem,
  SidebarMenuSkeleton,
} from "@/modules/common/ui/sidebar";

export function BookingSidebarSkeleton() {
  const { t } = useTranslation("common");
  return (
    <SidebarGroup aria-busy="true">
      <p role="status" className="sr-only">
        {t("loading")}
      </p>
      <SidebarGroupContent aria-hidden="true">
        <SidebarMenu>
          {[0, 1, 2, 3, 4, 5].map((row) => (
            <SidebarMenuItem key={row}>
              <SidebarMenuSkeleton showIcon />
            </SidebarMenuItem>
          ))}
        </SidebarMenu>
      </SidebarGroupContent>
    </SidebarGroup>
  );
}
