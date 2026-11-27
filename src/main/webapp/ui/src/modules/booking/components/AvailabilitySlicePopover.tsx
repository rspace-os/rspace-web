import * as React from "react";
import { Popover, PopoverContent, PopoverTrigger } from "@/modules/common/ui/popover";
import { cn } from "@/modules/common/utils/cn";

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
  const popupRef = React.useRef<HTMLDivElement>(null);
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
        closeDelay={500}
        aria-label={label}
        onFocus={(event) => {
          if (!event.currentTarget.matches(":focus-visible")) return;
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
      <PopoverContent
        ref={popupRef}
        initialFocus={popupRef}
        side="bottom"
        align="start"
        sideOffset={8}
        collisionPadding={8}
        sticky
        className="max-h-[min(32rem,var(--available-height))] w-80 gap-3 overflow-y-auto overscroll-contain rounded-sm p-3 duration-0 data-closed:animate-none data-open:animate-none"
      >
        {children}
      </PopoverContent>
    </Popover>
  );
}
