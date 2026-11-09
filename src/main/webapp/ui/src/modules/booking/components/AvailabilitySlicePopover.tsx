import { type AvailabilityInterval, type AvailabilitySource, type AvailabilityState, buildAvailabilitySlices, timestamp } from "@/modules/booking/domain/availability";
import { formatList } from "@/modules/common/i18n/listFormat";
import { buttonVariants } from "@/modules/common/ui/button";
import { InventoryItem, InventoryLocationLink } from "@/modules/common/ui/inventory-item";
import { Popover, PopoverContent, PopoverDescription, PopoverTitle, PopoverTrigger } from "@/modules/common/ui/popover";
import { UserBadge } from "@/modules/common/ui/user-badge";
import { cn } from "@/modules/common/utils/cn";
import { Popover as PopoverPrimitive } from "@base-ui/react/popover";
import { Temporal } from "@js-temporal/polyfill";
import { Link } from "@tanstack/react-router";
import { CalendarClockIcon, ChevronRightIcon, WrenchIcon } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";

export function AvailabilitySlicePopover({
  children,
  className,
  label,
  left,
  width,
}: {
  children: React.ReactNode;
  className: string;
  label: string;
  left: string;
  width: string;
}) {
  const [open, setOpen] = React.useState(false);
  const suppressRestoredFocusRef = React.useRef(false);
  return (
    <Popover
      open={open}
      onOpenChange={(nextOpen, eventDetails) => {
        if (!nextOpen && eventDetails.reason === "escape-key") suppressRestoredFocusRef.current = true;
        setOpen(nextOpen);
      }}
    >
      <PopoverTrigger
        type="button"
        openOnHover
        delay={0}
        closeDelay={200}
        aria-label={label}
        onFocus={() => {
          if (suppressRestoredFocusRef.current) {
            suppressRestoredFocusRef.current = false;
            return;
          }
          setOpen(true);
        }}
        className={cn(
          "pointer-events-auto absolute inset-y-0 cursor-help border-2 border-transparent bg-transparent p-0 outline-none data-popup-open:border-primary focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring/50",
          className,
        )}
        style={{ left, width }}
      />
      {children}
    </Popover>
  );
}
