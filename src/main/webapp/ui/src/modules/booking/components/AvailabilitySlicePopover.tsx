import * as React from "react";
import { Popover, PopoverTrigger } from "@/modules/common/ui/popover";
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
