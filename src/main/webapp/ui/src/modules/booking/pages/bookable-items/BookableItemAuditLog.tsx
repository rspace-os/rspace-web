import { useQuery } from "@tanstack/react-query";
import { AlertTriangleIcon, CalendarRangeIcon, RefreshCwIcon, SearchIcon } from "lucide-react";
import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { ApiV2ProblemError } from "@/modules/common/api/v2/ApiV2ProblemError";
import { getListBookingConfigurationAuditEventsQueryKey } from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { useTableList } from "@/modules/common/table-list/hooks/state";
import { TableList } from "@/modules/common/table-list/TableList";
import { Alert, AlertDescription, AlertTitle } from "@/modules/common/ui/alert";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { InputGroup, InputGroupAddon, InputGroupText } from "@/modules/common/ui/input-group";
import { UserBadge } from "@/modules/common/ui/user-badge";
import { AuditTimestamp, RecordedValues } from "./BookableItemAuditEvent";
import {
  type AuditDateError,
  type AuditDateField,
  type AuditDateRange,
  type AuditRow,
  auditPresetRange,
  auditRangeToQuery,
  fetchBookingConfigurationAudit,
  validateAuditDateRange,
} from "./bookableItemAudit";

const PRESET_DAYS = [7, 30, 90] as const;

const RANGE_SEPARATOR = "\u2013";

const auditEventConfig = resolveCollectionConfig<AuditRow>({
  slug: "booking-configuration-audit",
  idField: "rowId",
  labels: {
    singularKey: "booking:bookableItemDetails.audit.singular",
    pluralKey: "booking:bookableItemDetails.audit.plural",
  },
  useAsTitle: "timestamp",
  defaultColumns: ["timestamp", "username", "action", "target", "payload"],
  listSearchableFields: ["username", "fullName", "action", "description"],
  fields: [
    { name: "rowId", type: "text", labelKey: "booking:bookableItemDetails.audit.fields.rowId", list: false },
    { name: "eventId", type: "text", labelKey: "booking:bookableItemDetails.audit.fields.eventId", list: false },
    {
      name: "timestamp",
      type: "text",
      labelKey: "booking:bookableItemDetails.audit.fields.timestamp",
      list: { width: 200, minWidth: 170, renderCell: ({ row }) => <AuditTimestamp value={row.timestamp} /> },
    },
    {
      name: "username",
      type: "text",
      labelKey: "booking:bookableItemDetails.audit.fields.actor",
      list: {
        width: 190,
        minWidth: 160,
        dependencies: ["fullName"],
        renderCell: ({ row }) => <UserBadge name={row.fullName ?? row.username} username={row.username} />,
      },
    },
    { name: "fullName", type: "text", labelKey: "booking:bookableItemDetails.audit.fields.fullName" },
    {
      name: "action",
      type: "text",
      labelKey: "booking:bookableItemDetails.audit.fields.action",
      list: {
        width: 300,
        minWidth: 220,
        dependencies: ["description"],
        renderCell: ({ row }) => (
          <span>
            <Badge variant="outline">{row.action}</Badge>
            {row.description === null || row.description === undefined ? null : (
              <span className="mt-2 block whitespace-normal text-muted-foreground">{row.description}</span>
            )}
          </span>
        ),
      },
    },
    { name: "description", type: "text", labelKey: "booking:bookableItemDetails.audit.fields.description" },
    { name: "domain", type: "text", labelKey: "booking:bookableItemDetails.audit.fields.domain", list: false },
    {
      name: "target",
      type: "text",
      labelKey: "booking:bookableItems.fields.id",
      list: {
        width: 120,
        minWidth: 100,
        dependencies: ["payload"],
        renderCell: ({ row }) => <span>{row.target ?? "—"}</span>,
      },
    },
    {
      name: "payload",
      type: "text",
      labelKey: "booking:bookableItemDetails.audit.fields.values",
      list: {
        width: 320,
        minWidth: 240,
        card: { fullWidth: true },
        renderCell: ({ row }) => <RecordedValues row={row} />,
      },
    },
  ],
});

type RequestError = "unavailable" | "generic";

function requestError(error: unknown): RequestError | null {
  if (!(error instanceof ApiV2ProblemError)) return error === null ? null : "generic";
  if (error.status === 503 || error.code === "errors.api.v2.audit.unavailable") return "unavailable";
  return "generic";
}

export function BookableItemAuditLog({ configurationId }: { configurationId: number }) {
  const { t, i18n } = useTranslation("booking");

  const [today] = useState(() => new Date());
  const [draft, setDraft] = useState<AuditDateRange>(() => auditPresetRange(30, today));
  const [applied, setApplied] = useState<AuditDateRange>(draft);
  const [errors, setErrors] = useState<Partial<Record<AuditDateField, AuditDateError>>>({});
  const [page, setPage] = useState(0);

  const fromRef = useRef<HTMLInputElement>(null);
  const toRef = useRef<HTMLInputElement>(null);
  const resultsHeadingRef = useRef<HTMLHeadingElement>(null);
  const bounds = auditRangeToQuery(applied);

  const audit = useQuery({
    queryKey: [
      ...getListBookingConfigurationAuditEventsQueryKey(configurationId, {
        ...bounds,
      }),
      "bookableItemAudit",
    ],
    queryFn: ({ signal }) =>
      fetchBookingConfigurationAudit({
        configurationId,
        ...bounds,

        signal,
      }),
    retry: false,
  });
  const errorKind = audit.isError ? requestError(audit.error) : null;

  const table = useTableList({
    config: auditEventConfig,
    dataSource: {
      type: "client",
      rows: audit.isError ? [] : (audit.data?.rows.slice(page * 20, (page + 1) * 20) ?? []),
    },
    features: { sorting: false, pagination: false, columns: false },
    queryString: false,
    reserveEmptyRows: false,
  });

  const resetResultSet = () => {
    setPage(0);
    void audit.refetch();
  };

  const apply = (range: AuditDateRange) => {
    const validation = validateAuditDateRange(range);
    setDraft(range);
    if (!validation.valid) {
      setErrors(validation.fields);
      (validation.fields.from ? fromRef : toRef).current?.focus();
      return;
    }
    setErrors({});
    setApplied(range);
    setPage(0);
  };

  const navigate = (nextPage: number) => setPage(nextPage);
  const totalPages = Math.max(1, Math.ceil((audit.data?.rows.length ?? 0) / 20));

  const activePreset = PRESET_DAYS.find((days) => {
    const range = auditPresetRange(days, today);
    return draft.from === range.from && draft.to === range.to;
  });
  const resultDate = audit.data
    ? new Date(new Date(audit.data.endExclusive).getTime() - 86400000).toISOString().slice(0, 10)
    : undefined;
  const stableEmpty = resultDate !== undefined && applied.from > resultDate;
  const formattedResultDate =
    resultDate === undefined
      ? null
      : new Intl.DateTimeFormat(i18n.language, { dateStyle: "medium", timeZone: "UTC" }).format(
          new Date(`${resultDate}T00:00:00Z`),
        );
  const truncatedText = audit.data?.truncated
    ? t("bookableItemDetails.audit.truncated", { limit: audit.data.limit })
    : "";
  const statusText = audit.isPending
    ? t("bookableItemDetails.audit.status.loading")
    : audit.isFetching
      ? t("bookableItemDetails.audit.status.refreshing")
      : audit.isSuccess
        ? [
            t("bookableItemDetails.audit.status.loaded", {
              page: page + 1,
              totalPages: totalPages,
              count: audit.data.rows.length,
              date: formattedResultDate,
            }),
            truncatedText,
          ]
            .filter(Boolean)
            .join(" ")
        : "";

  const dateError = (field: AuditDateField) => {
    const error = errors[field];
    if (error === undefined) return null;
    const key = {
      required: "bookableItemDetails.audit.validation.required",
      invalid: "bookableItemDetails.audit.validation.invalid",
      inverted: "bookableItemDetails.audit.validation.inverted",
      tooWide: "bookableItemDetails.audit.validation.tooWide",
    } as const;
    return t(key[error]);
  };
  const fromError = dateError("from");
  const toError = dateError("to");

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center gap-2">
        <span className="mr-1 text-sm font-medium">{t("bookableItemDetails.audit.period")}</span>
        {PRESET_DAYS.map((days) => (
          <Button
            key={days}
            type="button"
            size="sm"
            variant={activePreset === days ? "default" : "outline"}
            className={activePreset === days ? "hover:bg-primary" : undefined}
            aria-pressed={activePreset === days}
            onClick={() => apply(auditPresetRange(days, today))}
          >
            {t("bookableItemDetails.audit.lastDays", { count: days })}
          </Button>
        ))}
        <div className="ml-auto flex min-w-0 flex-wrap items-start gap-2">
          <div>
            <InputGroup className="w-auto max-w-full flex-wrap sm:flex-nowrap">
              <InputGroupAddon>
                <CalendarRangeIcon aria-hidden="true" />
              </InputGroupAddon>
              <input
                ref={fromRef}
                aria-label={t("bookableItemDetails.audit.from")}
                aria-invalid={errors.from !== undefined}
                aria-describedby={fromError ? "audit-from-error" : undefined}
                className="h-9 min-w-32 bg-transparent text-sm outline-none"
                type="date"
                value={draft.from}
                onChange={(event) => setDraft({ ...draft, from: event.currentTarget.value })}
              />
              <InputGroupText aria-hidden="true" className="px-2">
                {RANGE_SEPARATOR}
              </InputGroupText>
              <input
                ref={toRef}
                aria-label={t("bookableItemDetails.audit.to")}
                aria-invalid={errors.to !== undefined}
                aria-describedby={toError ? "audit-to-error" : undefined}
                className="h-9 min-w-32 bg-transparent pr-3 text-sm outline-none"
                type="date"
                value={draft.to}
                onChange={(event) => setDraft({ ...draft, to: event.currentTarget.value })}
              />
            </InputGroup>
            {fromError ? (
              <p id="audit-from-error" className="mt-1 text-sm text-destructive">
                {t("bookableItemDetails.audit.fromError", { message: fromError })}
              </p>
            ) : null}
            {toError ? (
              <p id="audit-to-error" className="mt-1 text-sm text-destructive">
                {t("bookableItemDetails.audit.toError", { message: toError })}
              </p>
            ) : null}
          </div>
          <Button type="button" className="hover:bg-primary" onClick={() => apply(draft)}>
            <SearchIcon aria-hidden="true" />
            {t("bookableItemDetails.audit.apply")}
          </Button>
        </div>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 ref={resultsHeadingRef} tabIndex={-1} className="text-lg font-semibold outline-none">
            {t("bookableItemDetails.audit.plural")}
          </h2>
          {formattedResultDate ? (
            <p className="text-sm text-muted-foreground">
              {t("bookableItemDetails.audit.resultsThrough", {
                date: formattedResultDate,
              })}
            </p>
          ) : null}
        </div>
        <Button type="button" variant="outline" onClick={resetResultSet}>
          <RefreshCwIcon aria-hidden="true" />
          {t("bookableItemDetails.audit.refresh")}
        </Button>
      </div>

      <p role="status" aria-live="polite" className="sr-only">
        {statusText}
      </p>

      {audit.data?.truncated ? <p className="text-sm text-muted-foreground">{truncatedText}</p> : null}

      {errorKind === "unavailable" ? (
        <Alert variant="destructive">
          <AlertTriangleIcon aria-hidden="true" />
          <AlertTitle>{t("bookableItemDetails.audit.unavailable.title")}</AlertTitle>
          <AlertDescription>{t("bookableItemDetails.audit.unavailable.description")}</AlertDescription>
        </Alert>
      ) : errorKind === "generic" ? (
        <Alert variant="destructive">
          <AlertTriangleIcon aria-hidden="true" />
          <AlertTitle>{t("bookableItemDetails.audit.error.title")}</AlertTitle>
          <AlertDescription>{t("bookableItemDetails.audit.error.description")}</AlertDescription>
        </Alert>
      ) : (
        <TableList
          {...table.tableProps}
          status={audit.isPending ? "loading" : audit.isFetching ? "refreshing" : "idle"}
          presentations={{ table: "wide", cards: "narrow" }}
          emptyDescription={
            stableEmpty ? t("bookableItemDetails.audit.emptyStable") : t("bookableItemDetails.audit.empty")
          }
          variant="transparent"
          hideHeader
        />
      )}

      {audit.isSuccess && totalPages > 1 ? (
        <nav aria-label={t("bookableItemDetails.audit.pagination")} className="flex flex-wrap items-center gap-3">
          <Button
            type="button"
            variant="outline"
            className="transition-colors"
            disabled={page === 0}
            aria-label={t("bookableItemDetails.audit.previousPage", {
              page,
            })}
            onClick={() => navigate(page - 1)}
          >
            {t("bookableItemDetails.audit.previous")}
          </Button>
          <span className="min-w-32 flex-1 text-center text-sm">
            {t("bookableItemDetails.audit.page", { page: page + 1, totalPages })}
          </span>
          <Button
            type="button"
            variant="outline"
            className="transition-colors"
            disabled={page + 1 >= totalPages}
            aria-label={t("bookableItemDetails.audit.nextPage", {
              page: page + 2,
            })}
            onClick={() => navigate(page + 1)}
          >
            {t("bookableItemDetails.audit.next")}
          </Button>
        </nav>
      ) : null}
    </div>
  );
}
