import { ArchiveIcon, EllipsisVerticalIcon, RotateCcwIcon, Trash2Icon } from "lucide-react";
import type { RefObject } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/modules/common/ui/button";
import { Menu, MenuContent, MenuItem, MenuSeparator, MenuTrigger } from "@/modules/common/ui/menu";
import type { BookingConfiguration } from "./bookingConfiguration";

export type BookingConfigurationLifecycleAction = "archive" | "restore" | "permanent-delete";

export function BookingConfigurationActionsMenu({
  configuration,
  itemName,
  directSysadmin,
  compact = false,
  disabled = false,
  triggerRef,
  onAction,
}: {
  configuration: {
    state?: BookingConfiguration["state"];
    capabilities?: BookingConfiguration["capabilities"];
  };
  itemName: string;
  directSysadmin: boolean;
  compact?: boolean;
  disabled?: boolean;
  triggerRef?: RefObject<HTMLButtonElement | null>;
  onAction: (action: BookingConfigurationLifecycleAction) => void;
}) {
  const { t } = useTranslation("booking");
  const editable = configuration.capabilities?.canEditConfiguration === true;
  const canPermanentlyDelete = directSysadmin;

  if (!editable && !canPermanentlyDelete) return null;

  return (
    <Menu>
      <MenuTrigger
        ref={triggerRef}
        disabled={disabled}
        render={
          <Button
            type="button"
            variant="outline"
            size={compact ? "icon-sm" : "icon-lg"}
            className={compact ? "rounded-sm" : "min-h-11 min-w-11 rounded-sm"}
            aria-label={t("bookableItems.actions.menu", { item: itemName })}
          >
            <EllipsisVerticalIcon aria-hidden="true" />
          </Button>
        }
      />
      <MenuContent className="w-64">
        {editable && configuration.state === "ACTIVE" ? (
          <MenuItem onClick={() => onAction("archive")}>
            <ArchiveIcon aria-hidden="true" />
            {t("bookableItems.actions.archive")}
          </MenuItem>
        ) : null}
        {editable && configuration.state === "ARCHIVED" ? (
          <MenuItem onClick={() => onAction("restore")}>
            <RotateCcwIcon aria-hidden="true" />
            {t("bookableItems.actions.restore")}
          </MenuItem>
        ) : null}
        {canPermanentlyDelete ? <MenuSeparator /> : null}
        {canPermanentlyDelete ? (
          <MenuItem className="text-destructive" onClick={() => onAction("permanent-delete")}>
            <Trash2Icon aria-hidden="true" />
            {t("bookableItems.actions.deletePermanently")}
          </MenuItem>
        ) : null}
      </MenuContent>
    </Menu>
  );
}
