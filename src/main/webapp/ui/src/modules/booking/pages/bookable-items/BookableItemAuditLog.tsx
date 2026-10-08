import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { AlertTriangleIcon, CalendarRangeIcon, ChevronRightIcon, RefreshCwIcon, SearchIcon } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { ApiV2ProblemError } from "@/modules/booking/domain/booking";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { TableList } from "@/modules/common/table-list/TableList";
import { useTableList } from "@/modules/common/table-list/useTableList";
import { Alert, AlertDescription, AlertTitle } from "@/modules/common/ui/alert";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/modules/common/ui/collapsible";
import { InputGroup, InputGroupAddon, InputGroupText } from "@/modules/common/ui/input-group";
import { UserBadge } from "@/modules/common/ui/user-badge";
import {
  AUDIT_ACTIONS,
  type AuditAction,
  type AuditDateError,
  type AuditDateField,
  type AuditDateRange,
  type AuditRow,
  type AuditSnapshot,
  auditPresetRange,
  auditRangeToQuery,
  fetchBookingConfigurationAudit,
  recordedValues,
  validateAuditDateRange,
} from "./bookableItemAudit";

const PRESET_DAYS = [7, 30, 90] as const;
const RANGE_SEPARATOR = "\u2013";

function RecordedValues({ row }: { row: AuditRow }) {
  const { t, i18n } = useTranslation("booking");
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
    slotGranularityMinutes: t("bookableItemDetails.audit.values.increment"),
    maxBookingDurationMinutes: t("bookableItemDetails.audit.values.maximumDuration"),
    bufferBeforeMinutes: t("bookableItemDetails.audit.values.bufferBefore"),
    bufferAfterMinutes: t("bookableItemDetails.audit.values.bufferAfter"),
    allowDoubleBooking: t("bookableItemDetails.audit.values.allowDoubleBooking"),
  };
  return (
    <dl className="grid min-w-0 grid-cols-[minmax(0,1fr)_minmax(0,1fr)] gap-x-3 gap-y-1 text-xs">
      {recordedValues(row.payload, i18n.language).map(([label, value]) => (
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

function AuditTarget({ target }: { target: AuditRow["target"] }) {
  const id = bookingId(target);
  return id === null ? (
    <span>{target ?? "—"}</span>
  ) : (
    <Link className="underline" to="/booking/calendar/bookings/$id" params={{ id }}>
      {target}
    </Link>
  );
}

function AuditEventItem({ row }: { row: AuditRow }) {
  const { t } = useTranslation(["booking", "common"]);
  const [open, setOpen] = useState(false);
  return (
    <li>
      <article aria-label={row.description ?? row.action} className="overflow-hidden rounded-sm border bg-card">
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
              <Badge variant="outline">{row.action}</Badge>
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

const auditEventConfig = resolveCollectionConfig<AuditRow>({
  slug: "booking-configuration-audit",
  idField: "rowId",
  labels: {
    singularKey: "booking:bookableItemDetails.audit.singular",
    pluralKey: "booking:bookableItemDetails.audit.plural",
  },
  useAsTitle: "timestamp",
  defaultColumns: ["timestamp", "username", "action", "target", "payload"],
  listSearchableFields: ["username", "fullName", "action", "description", "target", "payload"],
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
        renderCell: ({ row }) => <AuditTarget target={row.target} />,
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

function AuditTimestamp({ value }: { value: string }) {
  const { i18n } = useTranslation("booking");
  return (
    <time dateTime={value}>
      {new Intl.DateTimeFormat(i18n.language, { dateStyle: "medium", timeStyle: "long", timeZone: "UTC" }).format(
        new Date(value),
      )}
    </time>
  );
}

type RequestError = "conflict" | "unavailable" | "tooMany" | "generic";

function requestError(error: unknown): RequestError | null {
  if (!(error instanceof ApiV2ProblemError)) return error === null ? null : "generic";
  if (error.status === 409 || error.code === "errors.api.v2.audit.snapshot.changed") return "conflict";
  if (error.status === 503 || error.code === "errors.api.v2.audit.unavailable") return "unavailable";
  if (error.code === "errors.api.v2.audit.results.tooMany") return "tooMany";
  return "generic";
}

export function BookableItemAuditLog({ configurationId }: { configurationId: number }) {
  const { t, i18n } = useTranslation("booking");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const [today] = useState(() => new Date());
  const [draft, setDraft] = useState<AuditDateRange>(() => auditPresetRange(30, today));
  const [applied, setApplied] = useState<AuditDateRange>(draft);
  const [search, setSearch] = useState("");
  const [action, setAction] = useState<AuditAction | "">("");
  const [errors, setErrors] = useState<Partial<Record<AuditDateField, AuditDateError>>>({});
  const [page, setPage] = useState(0);
  const [snapshot, setSnapshot] = useState<AuditSnapshot>();
  const [generation, setGeneration] = useState(0);
  const fromRef = useRef<HTMLInputElement>(null);
  const toRef = useRef<HTMLInputElement>(null);
  const conflictRef = useRef<HTMLDivElement>(null);
  const resultsHeadingRef = useRef<HTMLHeadingElement>(null);
  const focusResultsAfterRestart = useRef(false);
  const bounds = auditRangeToQuery(applied);

  const audit = useQuery({
    queryKey: [
      "api-v2",
      "booking-configurations",
      configurationId,
      "audit",
      applied.from,
      applied.to,
      search,
      action,
      page,
      snapshot?.snapshotDate ?? null,
      snapshot?.snapshotFingerprint ?? null,
      generation,
    ],
    queryFn: ({ signal }) =>
      fetchBookingConfigurationAudit({
        configurationId,
        ...bounds,
        search,
        actions: action === "" ? undefined : [action],
        page,
        snapshot,
        token,
        signal,
      }),
    retry: false,
  });
  const errorKind = audit.isError ? requestError(audit.error) : null;

  useEffect(() => {
    if (errorKind === "conflict") conflictRef.current?.focus();
  }, [errorKind]);

  useEffect(() => {
    if (focusResultsAfterRestart.current && audit.isSuccess) {
      focusResultsAfterRestart.current = false;
      resultsHeadingRef.current?.focus();
    }
  }, [audit.isSuccess]);

  const table = useTableList({
    config: auditEventConfig,
    dataSource: { type: "client", rows: audit.isError ? [] : (audit.data?.rows ?? []) },
    initialState: { filters: { search, expression: null } },
    features: { sorting: false, pagination: false, columns: false },
    queryString: false,
    reserveEmptyRows: false,
  });

  useEffect(() => {
    const nextSearch = table.state.filters.search;
    if (nextSearch === search) return;
    setSearch(nextSearch);
    setSnapshot(undefined);
    setPage(0);
    setGeneration((value) => value + 1);
  }, [search, table.state.filters.search]);

  const resetResultSet = () => {
    setSnapshot(undefined);
    setPage(0);
    setGeneration((value) => value + 1);
  };

  const selectAction = (value: string) => {
    setAction(AUDIT_ACTIONS.find((candidate) => candidate === value) ?? "");
    resetResultSet();
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
    resetResultSet();
  };

  const navigate = (nextPage: number) => {
    if (!audit.data) return;
    setSnapshot(
      snapshot ?? {
        snapshotDate: audit.data.snapshotDate,
        snapshotFingerprint: audit.data.snapshotFingerprint,
      },
    );
    setPage(nextPage);
  };

  const restart = () => {
    focusResultsAfterRestart.current = true;
    resetResultSet();
  };

  const activePreset = PRESET_DAYS.find((days) => {
    const range = auditPresetRange(days, today);
    return draft.from === range.from && draft.to === range.to;
  });
  const resultDate = audit.data?.snapshotDate;
  const stableEmpty = resultDate !== undefined && applied.from > resultDate;
  const formattedResultDate =
    resultDate === undefined
      ? null
      : new Intl.DateTimeFormat(i18n.language, { dateStyle: "medium", timeZone: "UTC" }).format(
          new Date(`${resultDate}T00:00:00Z`),
        );
  const statusText = audit.isPending
    ? t("bookableItemDetails.audit.status.loading")
    : audit.isFetching
      ? t("bookableItemDetails.audit.status.refreshing")
      : audit.isSuccess
        ? t("bookableItemDetails.audit.status.loaded", {
            page: page + 1,
            totalPages: Math.max(audit.data.totalPages, 1),
            count: audit.data.totalDocs,
            date: formattedResultDate,
          })
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
        <label className="flex items-center gap-2 text-sm font-medium" htmlFor="audit-action-filter">
          {t("bookableItemDetails.audit.fields.action")}
          <select
            id="audit-action-filter"
            className="h-9 rounded-sm border bg-background px-2 text-sm font-normal focus-visible:ring-2 focus-visible:ring-ring"
            value={action}
            onChange={(event) => selectAction(event.currentTarget.value)}
          >
            <option value="">{t("bookableItemDetails.audit.allActions")}</option>
            {AUDIT_ACTIONS.map((auditAction) => (
              <option key={auditAction} value={auditAction}>
                {auditAction}
              </option>
            ))}
          </select>
        </label>
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

      {errorKind === "conflict" ? (
        <Alert ref={conflictRef} tabIndex={-1}>
          <AlertTriangleIcon aria-hidden="true" />
          <AlertTitle>{t("bookableItemDetails.audit.conflict.title")}</AlertTitle>
          <AlertDescription>
            {t("bookableItemDetails.audit.conflict.description")}
            <div className="mt-4">
              <Button type="button" size="sm" className="hover:bg-primary" onClick={restart}>
                {t("bookableItemDetails.audit.restart")}
              </Button>
            </div>
          </AlertDescription>
        </Alert>
      ) : errorKind === "unavailable" ? (
        <Alert variant="destructive">
          <AlertTriangleIcon aria-hidden="true" />
          <AlertTitle>{t("bookableItemDetails.audit.unavailable.title")}</AlertTitle>
          <AlertDescription>{t("bookableItemDetails.audit.unavailable.description")}</AlertDescription>
        </Alert>
      ) : errorKind === "tooMany" ? (
        <Alert variant="destructive">
          <AlertTriangleIcon aria-hidden="true" />
          <AlertTitle>{t("bookableItemDetails.audit.tooMany.title")}</AlertTitle>
          <AlertDescription>{t("bookableItemDetails.audit.tooMany.description")}</AlertDescription>
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
          clientSide={false}
          status={audit.isPending ? "loading" : audit.isFetching ? "refreshing" : "idle"}
          presentations={{ table: "wide", cards: "narrow" }}
          emptyDescription={
            stableEmpty ? t("bookableItemDetails.audit.emptyStable") : t("bookableItemDetails.audit.empty")
          }
          variant="transparent"
          hideHeader
          hideFilterPanel
          renderRows={(rows) => (
            <ol className="space-y-3 py-3">
              {rows.map((row) => (
                <AuditEventItem key={row.rowId} row={row} />
              ))}
            </ol>
          )}
        />
      )}

      {audit.isSuccess && audit.data.totalPages > 1 ? (
        <nav aria-label={t("bookableItemDetails.audit.pagination")} className="flex flex-wrap items-center gap-3">
          <Button
            type="button"
            variant="outline"
            disabled={!audit.data.hasPrevPage}
            aria-label={t("bookableItemDetails.audit.previousPage", {
              page,
            })}
            onClick={() => navigate(page - 1)}
          >
            {t("bookableItemDetails.audit.previous")}
          </Button>
          <span className="min-w-32 flex-1 text-center text-sm">
            {t("bookableItemDetails.audit.page", { page: page + 1, totalPages: audit.data.totalPages })}
          </span>
          <Button
            type="button"
            variant="outline"
            disabled={!audit.data.hasNextPage}
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
