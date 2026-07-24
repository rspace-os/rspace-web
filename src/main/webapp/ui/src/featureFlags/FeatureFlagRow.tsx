import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/modules/common/ui/button";
import { Switch } from "@/modules/common/ui/switch";
import { TableCell, TableRow } from "@/modules/common/ui/table";
import type { FeatureFlagName } from "./generatedFeatureFlags";
import type { FeatureFlagEntry } from "./schema";

type FeatureFlagRowProps = {
  flagName: FeatureFlagName;
  entry: FeatureFlagEntry;
  canChangeBaselines: boolean;
  mutationPending: boolean;
  onOverride: (value: boolean) => void;
  onClearOverride: () => void;
  onBaseline: (value: boolean) => void;
};

export default function FeatureFlagRow({
  flagName,
  entry,
  canChangeBaselines,
  mutationPending,
  onOverride,
  onClearOverride,
  onBaseline,
}: FeatureFlagRowProps) {
  const { t } = useTranslation("common");
  const [baselineValue, setBaselineValue] = useState(entry.baselineValue);
  const canEditBaseline = canChangeBaselines && entry.source !== "PROPERTIES_FILE";

  return (
    <TableRow>
      <TableCell>{flagName}</TableCell>
      <TableCell>
        <div className="flex items-center gap-2">
          <Switch
            checked={entry.value}
            disabled={!entry.canOverride || mutationPending}
            onCheckedChange={onOverride}
            aria-label={t("featureFlags.overrideLabel", { flagName })}
          />
          {entry.canOverride && entry.source !== "USER_OVERRIDE" && (
            <Button variant="ghost" size="sm" disabled={mutationPending} onClick={() => onOverride(entry.value)}>
              {t("actions.set")}
            </Button>
          )}
          {entry.canOverride && entry.source === "USER_OVERRIDE" && (
            <Button variant="ghost" size="sm" disabled={mutationPending} onClick={onClearOverride}>
              {t("actions.clear")}
            </Button>
          )}
        </div>
      </TableCell>
      <TableCell>{entry.source}</TableCell>
      <TableCell>
        {canEditBaseline ? (
          <div className="flex items-center gap-2">
            <Switch
              checked={baselineValue}
              disabled={mutationPending}
              onCheckedChange={setBaselineValue}
              aria-label={t("featureFlags.baseline.controlLabel", { flagName })}
            />
            <span className="text-muted-foreground w-7 text-sm">
              {t(baselineValue ? "featureFlags.values.on" : "featureFlags.values.off")}
            </span>
            <Button
              size="sm"
              disabled={mutationPending || baselineValue === entry.baselineValue}
              onClick={() => onBaseline(baselineValue)}
            >
              {t("actions.save")}
            </Button>
          </div>
        ) : (
          <span className="text-sm">
            {t(entry.baselineValue ? "featureFlags.values.on" : "featureFlags.values.off")}
          </span>
        )}
      </TableCell>
    </TableRow>
  );
}
