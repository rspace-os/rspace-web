import { WrenchIcon } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Button } from "@/modules/common/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { getRelativeTime } from "@/stores/definitions/Units";

export default function MaintenanceNotice({ startDate }: { startDate: Date }) {
  const { t } = useTranslation("common");
  const message = t("appBar.maintenancePopup", { relativeTime: getRelativeTime(startDate) });
  return (
    <Tooltip>
      <TooltipTrigger render={<Button variant="ghost" size="icon-sm" aria-label={message} />}>
        <WrenchIcon className="text-destructive" />
      </TooltipTrigger>
      <TooltipContent>{message}</TooltipContent>
    </Tooltip>
  );
}
