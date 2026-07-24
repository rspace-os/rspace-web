import { Info, TriangleAlert } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { Alert, AlertDescription } from "@/modules/common/ui/alert";
import { Button } from "@/modules/common/ui/button";
import { Table, TableBody, TableHead, TableHeader, TableRow } from "@/modules/common/ui/table";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { cn } from "@/modules/common/utils/cn";
import FeatureFlagRow from "./FeatureFlagRow";
import {
  useClearFeatureFlagOverrideMutation,
  useSetFeatureFlagBaselineMutation,
  useSetFeatureFlagOverrideMutation,
} from "./mutations";
import { useFeatureFlags } from "./queries";
import { featureFlagNames } from "./schema";

export default function FeatureFlagPanel({ theme }: { theme: "light" | "dark" }) {
  const { t } = useTranslation("common");
  const featureFlags = useFeatureFlags();
  const { data: currentUser } = useCurrentUserQuery();
  const overrideMutation = useSetFeatureFlagOverrideMutation();
  const clearOverrideMutation = useClearFeatureFlagOverrideMutation();
  const baselineMutation = useSetFeatureFlagBaselineMutation();
  const [hasChanges, setHasChanges] = useState(false);
  const canChangeBaselines = currentUser.session.canChangeFeatureFlagBaselines;
  const mutationPending = overrideMutation.isPending || clearOverrideMutation.isPending || baselineMutation.isPending;
  const requestError =
    featureFlags.error ?? overrideMutation.error ?? clearOverrideMutation.error ?? baselineMutation.error;
  const markChanged = () => setHasChanges(true);

  return (
    <div className={cn("h-full bg-background text-foreground", theme === "dark" && "dark")}>
      <TooltipProvider>
        <div className="flex min-w-[620px] flex-col gap-4 overflow-auto p-4">
          {canChangeBaselines && (
            <Alert>
              <TriangleAlert />
              <AlertDescription>{t("featureFlags.warning")}</AlertDescription>
            </Alert>
          )}
          {requestError && (
            <Alert variant="destructive">
              <TriangleAlert />
              <AlertDescription>{requestError.message}</AlertDescription>
            </Alert>
          )}
          {hasChanges && (
            <Button variant="outline" size="sm" className="self-start" onClick={() => window.location.reload()}>
              {t("featureFlags.reload.action")}
            </Button>
          )}
          <Table aria-label={t("featureFlags.tableLabel")}>
            <TableHeader>
              <TableRow>
                <TableHead>{t("featureFlags.columns.name")}</TableHead>
                <TableHead>{t("featureFlags.columns.myValue")}</TableHead>
                <TableHead>{t("featureFlags.columns.source")}</TableHead>
                <TableHead>
                  <span className="inline-flex items-center gap-1">
                    {t("featureFlags.baseline.label")}
                    <Tooltip>
                      <TooltipTrigger
                        render={
                          <button
                            type="button"
                            aria-label={t("featureFlags.baseline.helpLabel")}
                            className="inline-flex"
                          />
                        }
                      >
                        <Info className="size-3.5 text-muted-foreground" />
                      </TooltipTrigger>
                      <TooltipContent>{t("featureFlags.baseline.description")}</TooltipContent>
                    </Tooltip>
                  </span>
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {featureFlagNames.map((flagName) => {
                const entry = featureFlags.data?.flags[flagName];
                if (!entry) return null;
                return (
                  <FeatureFlagRow
                    key={`${flagName}:${entry.baselineValue}`}
                    flagName={flagName}
                    entry={entry}
                    canChangeBaselines={canChangeBaselines}
                    mutationPending={mutationPending}
                    onOverride={(value) => overrideMutation.mutate({ flagName, value }, { onSuccess: markChanged })}
                    onClearOverride={() => clearOverrideMutation.mutate({ flagName }, { onSuccess: markChanged })}
                    onBaseline={(value) => baselineMutation.mutate({ flagName, value }, { onSuccess: markChanged })}
                  />
                );
              })}
            </TableBody>
          </Table>
        </div>
      </TooltipProvider>
    </div>
  );
}
