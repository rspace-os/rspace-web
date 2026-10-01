import { Link } from "@tanstack/react-router";
import { ArchiveIcon, EyeIcon } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { cn } from "@/modules/common/utils/cn";
import {
  BookingConfigurationActionsMenu,
  type BookingConfigurationLifecycleAction,
} from "./BookingConfigurationActionsMenu";
import type { BookingConfigurationRow } from "./bookingConfiguration";

export function BookableItemActionTriggers({
  configuration,
  activate,
  directSysadmin,
  onRestore,
}: {
  configuration: BookingConfigurationRow;
  activate: (actionId: string) => void;
  directSysadmin: boolean;
  onRestore: (configuration: BookingConfigurationRow) => Promise<void>;
}) {
  const { t } = useTranslation(["booking", "common"]);
  const itemName = configuration.target?.value.name ?? t("common:values.unknownItem");

  return (
    <div className="flex items-center justify-start gap-1">
      {configuration.target === null ? null : (
        <Link
          to="/booking/bookable-items/$globalId/{-$tab}"
          params={{ globalId: configuration.target.globalId, tab: undefined }}
          aria-label={t("bookableItems.actions.viewDetails", { item: itemName })}
          className={cn(buttonVariants({ variant: "ghost", size: "icon-sm" }), "rounded-sm")}
          data-slot="button"
        >
          <EyeIcon aria-hidden="true" />
        </Link>
      )}
      {configuration.state === "ACTIVE" && !directSysadmin ? (
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          className="rounded-sm text-destructive"
          aria-label={t("bookableItems.actions.archive")}
          onClick={() => activate("archive")}
        >
          <ArchiveIcon aria-hidden="true" />
        </Button>
      ) : (
        <BookingConfigurationActionsMenu
          configuration={configuration}
          itemName={itemName}
          directSysadmin={directSysadmin}
          compact
          onAction={(action: BookingConfigurationLifecycleAction) => {
            if (action === "restore") void onRestore(configuration);
            else activate(action);
          }}
        />
      )}
    </div>
  );
}
