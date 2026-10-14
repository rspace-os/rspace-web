import { CircleHelpIcon } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";

export function FieldHelpTooltip({ label, children }: { label: string; children: ReactNode }) {
  const { t } = useTranslation("common");
  return (
    <Tooltip>
      <TooltipTrigger
        render={
          <button
            type="button"
            aria-label={t("collectionForm.fieldHelp", { label })}
            className="inline-flex size-6 shrink-0 items-center justify-center rounded-sm text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring"
          />
        }
      >
        <CircleHelpIcon aria-hidden="true" className="size-3.5" />
      </TooltipTrigger>
      <TooltipContent role="tooltip">{children}</TooltipContent>
    </Tooltip>
  );
}
