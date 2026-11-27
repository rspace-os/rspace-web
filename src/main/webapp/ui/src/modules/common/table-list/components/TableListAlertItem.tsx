import { Undo2Icon, XIcon } from "lucide-react";
import { useId } from "react";
import { useTranslation } from "react-i18next";
import { alertToneVariants } from "@/modules/common/ui/alert";
import { Button } from "@/modules/common/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { cn } from "@/modules/common/utils/cn";
import type { TableListAlert } from "./TableListAlerts";

export type TableListAlertItemProps = {
  alert: TableListAlert & { status?: "idle" | "undoing" | "failed"; failure?: string };
  onUndo?: (id: string) => void;
  onDismiss: (id: string) => void;
};

export function TableListAlertItem({ alert, onUndo, onDismiss }: TableListAlertItemProps) {
  const { t } = useTranslation("common");
  const messageId = useId();
  const undoing = alert.status === "undoing";
  const failed = alert.status === "failed";
  const dismissLabel = t("tableList.alerts.dismiss");
  return (
    <li
      data-table-list-alert={alert.id}
      tabIndex={-1}
      aria-labelledby={messageId}
      className={cn(
        "flex flex-wrap items-center gap-x-3 gap-y-2 rounded-sm border px-4 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
        alertToneVariants({ variant: failed ? "destructive" : (alert.tone ?? "default") }),
      )}
    >
      {alert.icon ? <span className="flex shrink-0 [&_svg]:size-4">{alert.icon}</span> : null}
      <p id={messageId} className="min-w-0 flex-1">
        {failed ? (alert.failure ?? t("tableList.alerts.undoFailed")) : alert.message}
      </p>
      {alert.actions ? <div className="flex flex-wrap items-center gap-2">{alert.actions}</div> : null}
      {alert.undo && !failed && onUndo ? (
        <Button
          type="button"
          size="sm"
          variant="outline"
          disabled={undoing}
          aria-busy={undoing}
          onClick={() => onUndo(alert.id)}
        >
          <Undo2Icon aria-hidden="true" data-icon="inline-start" />
          {undoing ? t("tableList.alerts.undoing") : (alert.undo.label ?? t("tableList.alerts.undo"))}
        </Button>
      ) : null}
      <Tooltip>
        <TooltipTrigger
          render={
            <Button
              type="button"
              size="icon-sm"
              variant="ghost"
              aria-label={dismissLabel}
              aria-describedby={messageId}
              disabled={undoing}
              onClick={() => onDismiss(alert.id)}
            />
          }
        >
          <XIcon aria-hidden="true" />
        </TooltipTrigger>
        <TooltipContent role="tooltip">{dismissLabel}</TooltipContent>
      </Tooltip>
    </li>
  );
}
