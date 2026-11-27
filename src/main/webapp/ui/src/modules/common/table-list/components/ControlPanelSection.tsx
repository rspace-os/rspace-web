import { XIcon } from "lucide-react";
import type { ReactNode } from "react";
import { Button } from "@/modules/common/ui/button";

/** A table control panel: its titled header with a close button, then its content. */
export function ControlPanelSection({
  controlPanelId,
  title,
  description,
  closeLabel,
  onClose,
  children,
}: {
  /** Unique per table, so several tables on one page have distinct panel ids. */
  controlPanelId: string;
  title: string;
  description?: ReactNode;
  closeLabel: string;
  onClose: () => void;
  children: ReactNode;
}) {
  return (
    <section
      id={controlPanelId}
      aria-labelledby={`${controlPanelId}-title`}
      className="rounded-sm border bg-popover p-4"
    >
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 id={`${controlPanelId}-title`} className="font-heading text-sm font-medium">
            {title}
          </h2>
          {description}
        </div>
        <Button aria-label={closeLabel} size="icon-xs" variant="ghost" onClick={onClose}>
          <XIcon aria-hidden="true" />
        </Button>
      </div>
      {children}
    </section>
  );
}
