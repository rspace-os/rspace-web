import { ChevronDownIcon, PlusIcon, WrenchIcon } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { useBookingCreationStore } from "@/modules/booking/creation/bookingCreationStore";
import type { BookingEventKind } from "@/modules/booking/domain/booking";
import type { BookingWindowDraft } from "@/modules/booking/domain/bookingTime";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { Button } from "@/modules/common/ui/button";
import { ButtonGroup } from "@/modules/common/ui/button-group";
import { Menu, MenuContent, MenuItem, MenuTrigger } from "@/modules/common/ui/menu";

export function BookingCreationButtonGroup({
  ownerId,
  target,
  initialDate,
  initialWindow,
  lockTarget = false,
  disabled = false,
  size = "sm",
}: {
  ownerId: string;
  target?: BookableItemOption;
  initialDate?: string;
  initialWindow?: BookingWindowDraft;
  lockTarget?: boolean;
  disabled?: boolean;
  /** "default" matches the page-heading create action other collection pages render. */
  size?: "sm" | "default";
}) {
  const { t } = useTranslation("booking");
  const { data: currentUser } = useCurrentUserQuery();
  const beginCreation = useBookingCreationStore((state) => state.beginCreation);
  const creationActive = useBookingCreationStore((state) => state.activeCreation !== null);
  const reactId = React.useId();
  const id = reactId.replaceAll(":", "");
  const bookingTriggerId = `${ownerId}-${id}-new-booking`;
  const maintenanceTriggerId = `${ownerId}-${id}-new-maintenance`;
  const canManageMaintenance = currentUser.hasSysAdminRole && !currentUser.session.operatedAs;
  const unavailable = disabled || creationActive;
  const iconSize = size === "sm" ? "icon-sm" : "icon";

  const begin = (eventKind: BookingEventKind, triggerId: string) => {
    beginCreation({
      ownerId,
      triggerId,
      eventKind,
      target,
      initialDate,
      window: initialWindow,
      lockTarget,
    });
  };

  if (!canManageMaintenance) {
    return (
      <Button
        id={bookingTriggerId}
        type="button"
        size={size}
        className="rounded-sm!"
        disabled={unavailable}
        onClick={() => begin("BOOKING", bookingTriggerId)}
      >
        <PlusIcon aria-hidden="true" data-icon="inline-start" className="size-3.5!" />
        {t("bookings.actions.newBooking")}
      </Button>
    );
  }

  return (
    <ButtonGroup
      aria-label={t("bookings.actions.createEvent")}
      className="[&>[data-slot]:first-child]:rounded-l-sm! [&>[data-slot]:not(:has(~[data-slot]))]:rounded-r-sm!"
    >
      <Button
        id={bookingTriggerId}
        type="button"
        size={size}
        disabled={unavailable}
        onClick={() => begin("BOOKING", bookingTriggerId)}
      >
        <PlusIcon aria-hidden="true" data-icon="inline-start" className="size-3.5!" />
        {t("bookings.actions.newBooking")}
      </Button>
      <Menu>
        <MenuTrigger
          id={maintenanceTriggerId}
          disabled={unavailable}
          render={
            <Button type="button" size={iconSize} aria-label={t("bookings.actions.moreCreationOptions")}>
              <ChevronDownIcon aria-hidden="true" className="size-3.5!" />
            </Button>
          }
        />
        <MenuContent className="w-64">
          <MenuItem onClick={() => begin("MAINTENANCE", maintenanceTriggerId)}>
            <WrenchIcon aria-hidden="true" className="size-3.5!" />
            {t("bookings.actions.newMaintenance")}
          </MenuItem>
        </MenuContent>
      </Menu>
    </ButtonGroup>
  );
}
