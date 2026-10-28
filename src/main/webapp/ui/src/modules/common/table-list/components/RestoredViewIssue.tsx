import { useTranslation } from "react-i18next";
import { Button } from "@/modules/common/ui/button";
import { cn } from "@/modules/common/utils/cn";
import type { TableListRestoredViewIssue } from "../state/tableListState";

export function RestoredViewIssue({
  issue,
  label,
  className,
}: {
  issue: TableListRestoredViewIssue;
  label?: string;
  className?: string;
}) {
  const { t } = useTranslation("common");
  return (
    <div role="alert" className={cn("space-y-2 border-b py-3 text-sm", className)}>
      <p>{issue.kind === "network" ? t("tableList.savedView.loadFailed") : t("tableList.savedView.invalid")}</p>
      {label ? <p className="text-xs text-muted-foreground">{label}</p> : null}
      {issue.encoded ? <pre className="whitespace-pre-wrap break-all text-xs">{issue.encoded}</pre> : null}
      <div className="flex gap-2">
        {issue.kind === "network" && issue.retry ? (
          <Button variant="outline" size="sm" onClick={issue.retry}>
            {t("tableList.savedView.retry")}
          </Button>
        ) : null}
        <Button variant="outline" size="sm" onClick={issue.remove}>
          {t("tableList.savedView.reset")}
        </Button>
      </div>
    </div>
  );
}
