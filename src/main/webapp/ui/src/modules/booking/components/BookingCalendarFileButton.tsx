import { CalendarArrowDownIcon } from "lucide-react";
import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { downloadBookingCalendarFile } from "@/modules/booking/domain/bookingCalendarFile";
import { Button } from "@/modules/common/ui/button";
import { Spinner } from "@/modules/common/ui/spinner";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { cn } from "@/modules/common/utils/cn";

type BookingCalendarFileButtonProps = {
  bookingId: number;
  /** Named in the accessible label, because a page usually offers several of these. */
  itemName: string;
  period: string;
  token: string;
  className?: string;
  size?: "xs" | "sm";
  /** "link" lets the button sit in an action row of link-styled siblings without standing out. */
  variant?: "outline" | "link";
  /** Replaces the visible text with an icon and exposes the short label in a tooltip. */
  iconOnly?: boolean;
};

/**
 * Saves one booking as a calendar file. The outcome is announced rather than shown because the
 * button often sits in a table row where extra text would shift the layout.
 */
export function BookingCalendarFileButton({
  bookingId,
  itemName,
  period,
  token,
  className,
  size = "sm",
  variant = "outline",
  iconOnly = false,
}: BookingCalendarFileButtonProps) {
  const { t } = useTranslation("booking");
  const active = useRef(false);
  const [pending, setPending] = useState(false);
  const [announcement, setAnnouncement] = useState("");
  const [failed, setFailed] = useState(false);
  const accessibleLabel = t("calendar.file.accessibleLabel", { item: itemName, period });
  const shortLabel = t("calendar.file.label");

  const download = async () => {
    if (active.current) return;
    active.current = true;
    setPending(true);
    setFailed(false);
    setAnnouncement(t("calendar.file.preparing", { item: itemName, period }));
    try {
      const filename = await downloadBookingCalendarFile(bookingId, token);
      setAnnouncement(t("calendar.file.downloaded", { filename }));
    } catch {
      setFailed(true);
      setAnnouncement(t("calendar.file.failed", { item: itemName, period }));
    } finally {
      active.current = false;
      setPending(false);
    }
  };

  const button = (
    <Button
      type="button"
      variant={variant}
      size={iconOnly ? "icon-lg" : size}
      aria-label={accessibleLabel}
      aria-disabled={pending || undefined}
      className={cn(pending && "cursor-progress", className)}
      onClick={() => {
        if (pending) return;
        void download();
      }}
    >
      {pending ? <Spinner className="size-4" aria-hidden="true" /> : <CalendarArrowDownIcon aria-hidden="true" />}
      {iconOnly ? null : shortLabel}
    </Button>
  );

  return (
    <>
      {iconOnly ? (
        <Tooltip>
          <TooltipTrigger render={button} />
          <TooltipContent role="tooltip">{shortLabel}</TooltipContent>
        </Tooltip>
      ) : (
        button
      )}
      {/* Two regions rather than one with a swapped role: an alert announces on insertion, a
          status only announces changes to a region that was already there. */}
      <span role="status" className="sr-only">
        {failed ? "" : announcement}
      </span>
      {failed ? (
        <span role="alert" className="sr-only">
          {announcement}
        </span>
      ) : null}
    </>
  );
}
