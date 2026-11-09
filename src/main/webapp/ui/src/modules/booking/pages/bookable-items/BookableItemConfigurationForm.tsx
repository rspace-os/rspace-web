import { Form, isDirty, reset, setInput, useForm } from "@formisch/react";
import { isEqual, pick } from "es-toolkit";
import { Fragment, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { formatIncrement } from "@/modules/booking/configuration/incrementFacts";
import {
  SchedulingSettingsFields,
  SchedulingSettingsSchema,
  schedulingSettingsEntries,
  schedulingSettingsFieldNames,
} from "@/modules/booking/configuration/schedulingSettings";
import { formatIsoWeekday } from "@/modules/booking/domain/bookingOpeningHours";
import { formatDurationMinutes, formatWallClockTime } from "@/modules/booking/domain/bookingTime";
import type { BookingConfigurationsUpdate } from "@/modules/common/api/v2/generated/models/bookingConfigurationsUpdate";
import { BookingConfigurationsUpdateSchema } from "@/modules/common/api/v2/generated/valibot/schemas";
import { RenderFields } from "@/modules/common/collection-form/RenderFields";
import { DirtyNavigationGuard } from "@/modules/common/navigation/DirtyNavigationGuard";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/modules/common/ui/alert-dialog";
import { Button } from "@/modules/common/ui/button";
import { type BookingConfiguration, bookingConfigurationFields } from "./bookingConfiguration";

const bookableItemConfigurationFormSchema = v.intersect([
  v.object({ ...BookingConfigurationsUpdateSchema.entries, enabled: v.boolean(), ...schedulingSettingsEntries }),
  SchedulingSettingsSchema,
]);

function configurationInput(configuration: BookingConfiguration) {
  return {
    ...pick(configuration, ["enabled", ...schedulingSettingsFieldNames]),
  };
}

const fieldLabels = {
  enabled: "bookableItemDetails.enabled",
  slotGranularityMinutes: "settings.fields.granularity",
  incrementUnit: "bookableItemDetails.fields.granularity",
  openingHours: "bookableItemDetails.fields.openingHours",
  bufferBeforeMinutes: "settings.fields.bufferBefore",
  bufferAfterMinutes: "settings.fields.bufferAfter",
  maxBookingDurationMinutes: "settings.fields.maximumDuration",
  allowDoubleBooking: "settings.fields.allowDoubleBooking",
} as const;

const editableFields = ["enabled", ...schedulingSettingsFieldNames] as const;
type EditableField = (typeof editableFields)[number];

type ConfigurationInput = ReturnType<typeof configurationInput>;

function configurationPatch(
  baseline: ConfigurationInput,
  draft: ConfigurationInput,
): Partial<BookingConfigurationsUpdate> {
  return Object.fromEntries(
    editableFields.filter((key) => !isEqual(baseline[key], draft[key])).map((key) => [key, draft[key]]),
  );
}

/** Keeps an opening baseline, then merges a conflicted draft with refreshed server values. */
export function BookableItemConfigurationForm({
  configuration,
  globalId,
  formId,
  pending,
  staleEdit,
  conflictVersion = null,
  failed,
  onSubmit,
  onDiscardConflictedDraft,
  onSaveBlockedChange,
}: {
  configuration: BookingConfiguration;
  globalId: string;
  formId: string;
  pending: boolean;
  staleEdit: boolean;
  /** The server version loaded after the last conflict, or null when the draft has not conflicted. */
  conflictVersion?: number | null;
  failed: boolean;
  onSubmit: (input: BookingConfigurationsUpdate, version: number) => Promise<unknown>;
  /** Called after the draft is replaced by the latest configuration, so the owner can clear the conflict. */
  onDiscardConflictedDraft?: () => void;
  /** True until a field changes; the owner disables Save. */
  onSaveBlockedChange?: (blocked: boolean) => void;
}) {
  const { t } = useTranslation(["booking", "common"]);
  const { t: bookingT } = useTranslation("booking");
  const serverValue = (field: EditableField): string => {
    switch (String(field)) {
      case "enabled":
      case "allowDoubleBooking":
        return baseline[field] ? t("common:actions.yes") : t("common:actions.no");
      case "slotGranularityMinutes":
      case "incrementUnit":
        return formatIncrement(baseline, bookingT);
      case "openingHours":
        if (baseline.openingHours.length === 0) return t("bookableItemDetails.openingHours.closed");
        return baseline.openingHours
          .map(
            ({ dayOfWeek, start, end }) =>
              `${formatIsoWeekday(dayOfWeek)}: ${formatWallClockTime(start)} – ${formatWallClockTime(end)}`,
          )
          .join("; ");
      case "maxBookingDurationMinutes":
        return baseline[field] === 0
          ? t("bookableItemDetails.unlimited")
          : formatDurationMinutes(Number(baseline[field]));
      default:
        return formatDurationMinutes(Number(baseline[field]));
    }
  };
  const [version, setVersion] = useState(configuration.configurationVersion);
  const [baseline, setBaseline] = useState(() => configurationInput(configuration));
  const attempted = useRef<ConfigurationInput | null>(null);
  const [conflicts, setConflicts] = useState<EditableField[]>([]);
  const [pendingDisable, setPendingDisable] = useState<{
    input: BookingConfigurationsUpdate;
    version: number;
  } | null>(null);
  // Remount on discard so remembered day hours do not outlive the draft.
  const [draftGeneration, setDraftGeneration] = useState(0);
  const form = useForm({
    schema: bookableItemConfigurationFormSchema,
    initialInput: configurationInput(configuration),
  });
  const dirty = isDirty(form);
  useEffect(() => {
    onSaveBlockedChange?.(
      !dirty || conflicts.length > 0 || (staleEdit && (conflictVersion === null || version !== conflictVersion)),
    );
  }, [dirty, conflicts.length, staleEdit, conflictVersion, version, onSaveBlockedChange]);
  useEffect(() => {
    if (
      !staleEdit ||
      conflictVersion === null ||
      configuration.configurationVersion !== conflictVersion ||
      conflictVersion === version ||
      !attempted.current
    )
      return;
    const server = configurationInput(configuration);
    const draft = attempted.current;
    const overlapping = editableFields.filter(
      (key) =>
        !isEqual(baseline[key], draft[key]) &&
        !isEqual(baseline[key], server[key]) &&
        !isEqual(draft[key], server[key]),
    );
    const merged = { ...server, ...configurationPatch(baseline, draft) };
    reset(form, { initialInput: server });
    setInput(form, { input: merged });
    setBaseline(server);
    setVersion(conflictVersion);
    setConflicts(overlapping);
    setDraftGeneration((generation) => generation + 1);
  }, [staleEdit, conflictVersion, configuration, version, baseline, form]);
  const discardDraft = () => {
    reset(form, { initialInput: configurationInput(configuration) });
    setVersion(configuration.configurationVersion);
    setBaseline(configurationInput(configuration));
    setConflicts([]);
    attempted.current = null;
    setPendingDisable(null);
    setDraftGeneration((generation) => generation + 1);
    onDiscardConflictedDraft?.();
    // The discard button leaves with the conflict alert, so move focus to the reloaded fields.
    requestAnimationFrame(() =>
      document
        .getElementById(formId)
        ?.querySelector<HTMLElement>("input:not([disabled]), button:not([disabled])")
        ?.focus(),
    );
  };
  return (
    <>
      <DirtyNavigationGuard
        dirty={dirty}
        shouldBlockNavigation={({ current, next }) =>
          current.pathname !== next.pathname &&
          (next.routeId !== "/booking/bookable-items/$globalId/{-$tab}/" ||
            next.params.globalId !== globalId ||
            !next.search.edit)
        }
      />
      <Form
        id={formId}
        of={form}
        className="min-w-0 space-y-4"
        onSubmit={(input) => {
          if (
            !dirty ||
            conflicts.length > 0 ||
            (staleEdit && (conflictVersion === null || version !== conflictVersion))
          )
            return;
          attempted.current = input;
          const patch = configurationPatch(baseline, input);
          if (baseline.enabled && patch.enabled === false) {
            setPendingDisable({ input: patch, version });
            return;
          }
          return onSubmit(patch, version);
        }}
      >
        <Fragment key={draftGeneration}>
          <RenderFields
            fields={bookingConfigurationFields.filter((field) => field.name !== "target" && field.name !== "timezone")}
            form={form}
            disabled={pending}
            layout="inline"
          />
          <SchedulingSettingsFields form={form} disabled={pending} layout="inline" />
        </Fragment>
        {staleEdit ? (
          <div className="space-y-2">
            <p role="alert" className="text-sm text-destructive">
              {t("bookableItems.staleEdit")}
            </p>
            {conflicts.map((field) => (
              <div key={field} className="space-y-2">
                <p>{t("bookableItems.mergeConflict", { field: t(fieldLabels[field]) })}</p>
                <p>{t("bookableItems.serverValue", { value: serverValue(field) })}</p>
                <Button
                  type="button"
                  size="sm"
                  disabled={pending}
                  onClick={() => setConflicts((current) => current.filter((key) => key !== field))}
                >
                  {t("bookableItems.keepDraft", {})}
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={pending}
                  onClick={() => {
                    setInput(form, { path: [field], input: baseline[field] });
                    setConflicts((current) => current.filter((key) => key !== field));
                  }}
                >
                  {t("bookableItems.useServer", {})}
                </Button>
              </div>
            ))}
            <Button type="button" size="sm" variant="outline" disabled={pending} onClick={discardDraft}>
              {t("bookableItems.actions.discardDraft")}
            </Button>
          </div>
        ) : failed ? (
          <p role="alert" className="text-sm text-destructive">
            {t("bookableItems.editError")}
          </p>
        ) : null}
      </Form>
      <AlertDialog
        open={pendingDisable !== null}
        onOpenChange={(open) => {
          if (!open) setPendingDisable(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("bookableItemDetails.disableDialog.title", {})}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("bookableItemDetails.disableDialog.description", {
                item: configuration.target?.value.name ?? t("common:values.unknownItem"),
              })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={pending}>{t("common:actions.cancel")}</AlertDialogCancel>
            <AlertDialogAction
              disabled={pending}
              onClick={() => {
                const submission = pendingDisable;
                setPendingDisable(null);
                if (submission) void onSubmit(submission.input, submission.version).catch(() => undefined);
              }}
            >
              {t("bookableItemDetails.disableDialog.confirm", {})}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
