import { useState } from "react";
import { useTranslation } from "react-i18next";
import type { TableListSelectionContext } from "@/modules/common/table-list/TableList";
import { Button } from "@/modules/common/ui/button";

export function BookingNotificationBulkActions({
  selection,
  selectedConfigurationIds,
  pending,
  onAction,
}: {
  selection: TableListSelectionContext;
  selectedConfigurationIds: readonly number[];
  pending: boolean;
  onAction: (configurationIds: readonly number[], enabled: boolean) => Promise<unknown>;
}) {
  const { t } = useTranslation("booking");
  const [failed, setFailed] = useState(false);
  const run = async (enabled: boolean) => {
    setFailed(false);
    try {
      await onAction(selectedConfigurationIds, enabled);
      selection.clearSelection();
    } catch {
      setFailed(true);
    }
  };

  return (
    <div className="flex flex-wrap items-center gap-2">
      <Button
        type="button"
        size="sm"
        disabled={pending || selectedConfigurationIds.length === 0}
        aria-busy={pending}
        onClick={() => void run(true)}
      >
        {t("notificationSubscriptions.bulk.subscribe")}
      </Button>
      <Button
        type="button"
        size="sm"
        variant="outline"
        disabled={pending || selectedConfigurationIds.length === 0}
        aria-busy={pending}
        onClick={() => void run(false)}
      >
        {t("notificationSubscriptions.bulk.unsubscribe")}
      </Button>
      {failed ? (
        <p role="alert" className="basis-full text-sm text-destructive">
          {t("notificationSubscriptions.bulk.error")}
        </p>
      ) : null}
    </div>
  );
}
