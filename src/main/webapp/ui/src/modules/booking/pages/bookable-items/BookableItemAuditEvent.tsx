import { Link } from "@tanstack/react-router";
import { ChevronRightIcon } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useBookingTimeFormat } from "@/modules/booking/domain/bookingDisplayPreferences";
import { bookingHourCycle } from "@/modules/booking/domain/bookingTime";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/modules/common/ui/collapsible";
import { UserBadge } from "@/modules/common/ui/user-badge";
import type { AuditEventKind, AuditRow } from "./bookableItemAudit";
import { auditEventKind, recordedValues } from "./bookableItemAudit";

/** Booking terms for audit events, so a cancellation is not shown as a generic WRITE. */
export function useAuditEventKindLabels(): Record<AuditEventKind, string> {
  const { t } = useTranslation("booking");
  return {
    created: t("bookableItemDetails.audit.actions.created"),
    changed: t("bookableItemDetails.audit.actions.changed"),
    cancelled: t("bookableItemDetails.audit.actions.cancelled"),
    deleted: t("bookableItemDetails.audit.actions.deleted"),
    restored: t("bookableItemDetails.audit.actions.restored"),
  };
}

function useAuditEventLabel(row: AuditRow): string {
  const labels = useAuditEventKindLabels();
  const kind = auditEventKind(row);
  return kind === null ? row.action : labels[kind];
}

export function AuditActionBadge({ row }: { row: AuditRow }) {
  const label = useAuditEventLabel(row);
  return <Badge variant={auditEventKind(row) === "cancelled" ? "secondary" : "outline"}>{label}</Badge>;
}

export function RecordedValues({ row }: { row: AuditRow }) {
  const { t, i18n } = useTranslation("booking");
  const timeFormat = useBookingTimeFormat();
  const labels: Record<string, string> = {
    start: t("bookableItemDetails.audit.values.start"),
    end: t("bookableItemDetails.audit.values.end"),
    kind: t("bookableItemDetails.audit.values.kind"),
    purpose: t("bookableItemDetails.audit.values.purpose"),
    state: t("bookableItemDetails.audit.values.state"),
    target: t("bookableItemDetails.audit.values.target"),
    bookingConfigurationId: t("bookableItemDetails.audit.values.configuration"),
    enabled: t("bookableItemDetails.audit.values.enabled"),
    timezone: t("bookableItemDetails.audit.values.timezone"),
    openingStart: t("bookableItemDetails.audit.values.openingStart"),
    openingEnd: t("bookableItemDetails.audit.values.openingEnd"),
    openDays: t("bookableItemDetails.audit.values.openDays"),
    openingExceptions: t("bookableItemDetails.audit.values.openingExceptions"),
    slotGranularityMinutes: t("bookableItemDetails.audit.values.increment"),
    maxBookingDurationMinutes: t("bookableItemDetails.audit.values.maximumDuration"),
    bufferBeforeMinutes: t("bookableItemDetails.audit.values.bufferBefore"),
    bufferAfterMinutes: t("bookableItemDetails.audit.values.bufferAfter"),
    allowDoubleBooking: t("bookableItemDetails.audit.values.allowDoubleBooking"),
  };
  const words = {
    yes: t("bookableItemDetails.yes"),
    no: t("bookableItemDetails.no"),
    values: {
      ACTIVE: t("bookableItems.states.active"),
      ARCHIVED: t("bookableItemDetails.archived"),
      CONFIRMED: t("bookings.details.confirmed"),
      CANCELLED: t("bookings.details.cancelled"),
      BOOKING: t("bookings.form.typeBooking"),
      MAINTENANCE: t("bookings.form.typeBlockout"),
    },
  };
  return (
    <dl className="grid min-w-0 grid-cols-[minmax(0,1fr)_minmax(0,1fr)] gap-x-3 gap-y-1 text-xs">
      {recordedValues(row.payload, i18n.language, words, timeFormat).map(([label, value]) => (
        <div className="contents" key={label}>
          <dt className="break-words text-muted-foreground">{labels[label] ?? label}</dt>
          <dd className="break-words">{value === "{}" ? t("bookableItemDetails.audit.values.empty") : value}</dd>
        </div>
      ))}
    </dl>
  );
}

function bookingId(target: string | null | undefined): string | null {
  const match = target?.match(/^bookings:(\d+)$/);
  return match?.[1] ?? null;
}

export function AuditTarget({ target }: { target: AuditRow["target"] }) {
  const id = bookingId(target);
  return id === null ? (
    <span>{target ?? "—"}</span>
  ) : (
    <Link className="underline" to="/booking/calendar/bookings/$id" params={{ id }}>
      {target}
    </Link>
  );
}

export function AuditTimestamp({ value }: { value: string }) {
  const { i18n } = useTranslation("booking");
  const timeFormat = useBookingTimeFormat();
  return (
    <time dateTime={value}>
      {new Intl.DateTimeFormat(i18n.language, {
        dateStyle: "medium",
        timeStyle: "long",
        hourCycle: bookingHourCycle(timeFormat),
        timeZone: "UTC",
      }).format(new Date(value))}
    </time>
  );
}

export function AuditEventItem({ row }: { row: AuditRow }) {
  const { t } = useTranslation(["booking", "common"]);
  const [open, setOpen] = useState(false);
  const label = useAuditEventLabel(row);
  return (
    <li>
      <article aria-label={row.description ?? label} className="overflow-hidden rounded-sm border bg-card">
        <Collapsible open={open} onOpenChange={setOpen}>
          <div className="space-y-3 p-4">
            <div className="flex items-start justify-between gap-3">
              <AuditTimestamp value={row.timestamp} />
              <CollapsibleTrigger
                render={
                  <Button type="button" size="sm" variant="ghost">
                    {t(open ? "common:actions.collapse" : "common:actions.expand")}
                    <ChevronRightIcon
                      aria-hidden="true"
                      className={`transition-transform ${open ? "rotate-90" : ""}`}
                    />
                  </Button>
                }
              />
            </div>
            <div>
              <AuditActionBadge row={row} />
              {row.description === null || row.description === undefined ? null : (
                <p className="mt-2 text-muted-foreground">{row.description}</p>
              )}
            </div>
            <dl className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)] items-center gap-4 text-sm">
              <div className="flex min-w-0 items-center gap-2">
                <dt className="shrink-0 text-muted-foreground">
                  {t("booking:bookableItemDetails.audit.fields.actor")}
                </dt>
                <dd className="min-w-0">
                  <UserBadge name={row.fullName ?? row.username} username={row.username} />
                </dd>
              </div>
              <div className="flex min-w-0 items-center gap-2">
                <dt className="shrink-0 text-muted-foreground">{t("booking:bookableItems.fields.id")}</dt>
                <dd className="min-w-0 truncate">
                  <AuditTarget target={row.target} />
                </dd>
              </div>
            </dl>
          </div>
          <CollapsibleContent className="border-t px-4 py-3">
            <h3 className="mb-2 font-medium">{t("booking:bookableItemDetails.audit.fields.values")}</h3>
            <RecordedValues row={row} />
          </CollapsibleContent>
        </Collapsible>
      </article>
    </li>
  );
}
