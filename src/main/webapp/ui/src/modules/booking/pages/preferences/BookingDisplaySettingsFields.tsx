import { useId, useMemo } from "react";
import { useTranslation } from "react-i18next";
import {
  type BookingDisplayPreferencesInput,
  type BookingTimezoneMode,
  bookingTimeZoneOptions,
} from "@/modules/booking/domain/bookingDisplayPreferences";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { Input } from "@/modules/common/ui/input";
import { Label } from "@/modules/common/ui/label";

export function BookingDisplaySettingsFields({
  value,
  onChange,
  browserTimezone,
  institutionTimezone,
  disabled = false,
}: {
  value: BookingDisplayPreferencesInput;
  onChange: (value: BookingDisplayPreferencesInput) => void;
  browserTimezone: string;
  institutionTimezone: string;
  disabled?: boolean;
}) {
  const { t } = useTranslation("booking");
  const id = useId();
  const timezoneListId = `${id}-timezones`;
  const timezoneOptions = useMemo(
    () => bookingTimeZoneOptions(browserTimezone, institutionTimezone, value.customTimezone),
    [browserTimezone, institutionTimezone, value.customTimezone],
  );
  const patch = (next: Partial<BookingDisplayPreferencesInput>) => onChange({ ...value, ...next });

  return (
    <div className="space-y-6">
      <fieldset className="space-y-3" disabled={disabled}>
        <legend className="font-medium">{t("preferences.availabilityWindow.legend")}</legend>
        <p className="text-sm text-muted-foreground">{t("preferences.availabilityWindow.description")}</p>
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor={`${id}-start`}>{t("preferences.availabilityWindow.start")}</Label>
            <Input
              id={`${id}-start`}
              type="time"
              required
              value={value.availabilityWindowStart}
              onChange={(event) => patch({ availabilityWindowStart: event.currentTarget.value })}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor={`${id}-end`}>{t("preferences.availabilityWindow.end")}</Label>
            <Input
              id={`${id}-end`}
              type="time"
              required={value.availabilityWindowEnd !== "24:00"}
              disabled={disabled || value.availabilityWindowEnd === "24:00"}
              value={value.availabilityWindowEnd === "24:00" ? "" : value.availabilityWindowEnd}
              onChange={(event) => patch({ availabilityWindowEnd: event.currentTarget.value })}
            />
            <Label className="flex items-center gap-2 font-normal">
              <Checkbox
                checked={value.availabilityWindowEnd === "24:00"}
                disabled={disabled}
                onCheckedChange={(checked) => patch({ availabilityWindowEnd: checked ? "24:00" : "18:00" })}
              />
              {t("preferences.availabilityWindow.endOfDay")}
            </Label>
          </div>
        </div>
      </fieldset>

      <fieldset className="space-y-3" disabled={disabled}>
        <legend className="font-medium">{t("preferences.timezone.legend")}</legend>
        {(
          [
            ["BROWSER", t("preferences.timezone.browser", { timezone: browserTimezone })],
            ["INSTITUTION", t("preferences.timezone.institution", { timezone: institutionTimezone })],
            ["CUSTOM", t("preferences.timezone.custom")],
          ] as const
        ).map(([mode, label]) => (
          <Label key={mode} className="flex items-start gap-3 font-normal">
            <input
              type="radio"
              name={`${id}-timezone-mode`}
              value={mode}
              checked={value.timezoneMode === mode}
              onChange={() =>
                patch({
                  timezoneMode: mode as BookingTimezoneMode,
                  customTimezone: mode === "CUSTOM" ? (value.customTimezone ?? "UTC") : null,
                })
              }
            />
            <span>{label}</span>
          </Label>
        ))}
        <div className="space-y-2 pl-6">
          <Label htmlFor={`${id}-custom-timezone`}>{t("preferences.timezone.customLabel")}</Label>
          <Input
            id={`${id}-custom-timezone`}
            role="combobox"
            aria-expanded="false"
            list={timezoneListId}
            value={value.customTimezone ?? ""}
            disabled={disabled || value.timezoneMode !== "CUSTOM"}
            onChange={(event) => patch({ customTimezone: event.currentTarget.value })}
          />
          <datalist id={timezoneListId}>
            {timezoneOptions.map((timeZone) => (
              <option key={timeZone} value={timeZone} />
            ))}
          </datalist>
        </div>
      </fieldset>
    </div>
  );
}
