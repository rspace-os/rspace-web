import type { Temporal } from "@js-temporal/polyfill";
import { WrenchIcon } from "lucide-react";
import { useTranslation } from "react-i18next";
import { formatRelativeTime } from "@/modules/common/i18n/localeFormat";
import { Button } from "@/modules/common/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";

export default function MaintenanceNotice({ startDate }: { startDate: Temporal.Instant }) {
  const { t } = useTranslation("common");
  const message = t("appBar.maintenancePopup", { relativeTime: formatRelativeTime(startDate, "en") });
  return (
    <Tooltip>
      <TooltipTrigger render={<Button variant="ghost" size="icon-sm" aria-label={message} />}>
        <WrenchIcon className="text-destructive" />
      </TooltipTrigger>
      <TooltipContent>{message}</TooltipContent>
    </Tooltip>
  );
}
