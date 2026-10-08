import { useQuery } from "@tanstack/react-query";
import { CalendarDays, ChevronLeft, ChevronRight } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { toTimelineEvent } from "@/modules/booking/components/toTimelineEvent";
import { VerticalDayTimeline } from "@/modules/booking/components/VerticalDayTimeline";
import { todayInTimeZone } from "@/modules/booking/domain/bookingDisplayPreferences";
import { type BookingWindowDraft, parsePlainDate, zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import { fetchDayBookings } from "@/modules/booking/domain/fetchDayBookings";
import { Button } from "@/modules/common/ui/button";
import { Sheet, SheetClose, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/modules/common/ui/sheet";
import type { BookableItemOption } from "./bookableItemOption";

function draftDay(value: string): string | undefined {
  try {
    return parsePlainDate(value).toString();
  } catch {
    return undefined;
  }
}

export function BookingDayTimelineAside({
  target,
  draft,
  timezone,
  token,
  disabledReason,
  onChange,
  onInteractionChange,
}: {
  target: BookableItemOption | undefined;
  draft: BookingWindowDraft;
  timezone: string;
  token: string;
  disabledReason?: string;
  onChange: (draft: BookingWindowDraft) => void;
  onInteractionChange?: (active: boolean) => void;
}) {
  const { t } = useTranslation(["booking", "common"]);
  const startDate = draftDay(draft.startDate);
  const [date, setDate] = useState(startDate ?? todayInTimeZone(timezone));
  const [mobileOpen, setMobileOpen] = useState(false);
  const [formVisible, setFormVisible] = useState(false);
  const asideRef = useRef<HTMLElement>(null);
  const desktopTimelineRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    setDate(startDate ?? todayInTimeZone(timezone));
  }, [startDate, timezone, target?.globalId]);
  useEffect(() => {
    const container = asideRef.current?.closest('[class~="@container"]');
    const desktopTimeline = desktopTimelineRef.current;
    if (!container || !desktopTimeline) return;
    const observer = new ResizeObserver(() => {
      if (getComputedStyle(desktopTimeline).display !== "none") setMobileOpen(false);
    });
    observer.observe(container);
    return () => observer.disconnect();
  }, []);
  useEffect(() => {
    const form = asideRef.current?.closest('[class~="@container"]')?.querySelector("form");
    if (!form) return;
    if (typeof IntersectionObserver === "undefined") {
      setFormVisible(true);
      return;
    }
    const observer = new IntersectionObserver(([entry]) => setFormVisible(entry.isIntersecting));
    observer.observe(form);
    return () => observer.disconnect();
  }, []);
  const bounds = zonedDayBounds(date, timezone);
  const schedule = useQuery({
    queryKey: ["api-v2", "bookings", "day-schedule", target?.globalId, bounds.start, bounds.end, token],
    queryFn: ({ signal }) => fetchDayBookings(target ? [target.globalId] : [], bounds, token, signal),
    enabled: Boolean(target && token),
    staleTime: 30_000,
  });
  const tabClassName = "h-14 w-14 gap-0 rounded-r-none rounded-l-xl border-r-0 px-0 shadow-lg";
  const timeline = (
    <VerticalDayTimeline
      date={date}
      onDateChange={setDate}
      timezone={timezone}
      itemName={target?.name}
      scheduleWindow={
        target
          ? { timezone: target.timezone, openingStart: target.openingStart, openingEnd: target.openingEnd }
          : undefined
      }
      events={(schedule.data ?? []).map((booking) => toTimelineEvent(booking, date, timezone))}
      schedule={
        !target
          ? { status: "no-target" }
          : schedule.isError
            ? { status: "error", onRetry: () => void schedule.refetch() }
            : schedule.isPending
              ? { status: "loading" }
              : { status: "success" }
      }
      editing={
        target
          ? {
              draft,
              targetKey: target.globalId,
              schedulingTimeZone: target.timezone,
              slotGranularityMinutes: target.slotGranularityMinutes,
              onChange,
              onInteractionChange,
              disabledReason,
            }
          : undefined
      }
    />
  );
  return (
    <aside ref={asideRef} className="min-w-0 @4xl:sticky @4xl:top-4 @4xl:self-start">
      <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
        <SheetTrigger
          render={
            <Button
              type="button"
              variant="outline"
              title={t("dayTimeline.vertical.schedule")}
              className={
                formVisible && !mobileOpen
                  ? `${tabClassName} fixed top-1/2 right-0 z-40 -translate-y-1/2 @4xl:hidden`
                  : "hidden"
              }
            />
          }
        >
          <ChevronLeft aria-hidden="true" className="size-6" />
          <CalendarDays aria-hidden="true" className="size-6" />
          <span className="sr-only">{t("dayTimeline.vertical.schedule")}</span>
        </SheetTrigger>
        <SheetContent
          side="right"
          className="overflow-visible p-3 pt-14 data-[side=right]:data-starting-style:translate-x-full data-[side=right]:data-ending-style:translate-x-full"
          style={{ width: "min(calc(100vw - 3.5rem), 30rem)", maxWidth: "none" }}
          showCloseButton={false}
        >
          <SheetHeader className="sr-only">
            <SheetTitle>{t("dayTimeline.vertical.schedule")}</SheetTitle>
          </SheetHeader>
          <SheetClose
            render={
              <Button
                type="button"
                variant="outline"
                title={t("common:actions.close")}
                className={`${tabClassName} absolute top-1/2 -left-14 z-[100]`}
                style={{ translate: "0 -50%" }}
              />
            }
          >
            <ChevronRight aria-hidden="true" className="size-6" />
            <CalendarDays aria-hidden="true" className="size-6" />
            <span className="sr-only">{t("common:actions.close")}</span>
          </SheetClose>
          <div className="min-h-0 flex-1 overflow-y-auto">{timeline}</div>
        </SheetContent>
      </Sheet>
      <div ref={desktopTimelineRef} className="hidden @4xl:block">
        {timeline}
      </div>
    </aside>
  );
}
