import type { ComponentProps } from "react";
import { useTranslation } from "react-i18next";
import { INCREMENT_UNIT_MINUTES, INCREMENT_UNITS, type IncrementUnit } from "@/modules/booking/domain/bookingIncrement";
import { MAX_BOOKING_DURATION_MINUTES } from "@/modules/booking/domain/bookingOpeningHours";
import { Button } from "@/modules/common/ui/button";
import { ButtonGroup } from "@/modules/common/ui/button-group";
import { Input } from "@/modules/common/ui/input";

const UNIT_LABEL_KEYS = {
  MINUTES: "settings.fields.incrementMinutes",
  HOURS: "settings.fields.incrementHours",
  DAYS: "settings.fields.incrementDays",
} as const satisfies Record<IncrementUnit, string>;

// Unit conversion can leave float noise (0.35 days = 503.99999999999994); fractional minutes stay invalid.
export const wholeMinutes = (value: number) => Number(value.toFixed(6));

/** A numeric duration whose selected unit preserves the typed quantity when changed. */
export function DurationUnitInput({
  minutes,
  unit,
  onMinutesChange,
  onUnitChange,
  unitLabel,
  daysAvailable = true,
  ...props
}: Omit<ComponentProps<typeof Input>, "value" | "onChange"> & {
  minutes: number | undefined;
  unit: IncrementUnit;
  onMinutesChange: (minutes: number | undefined) => void;
  onUnitChange: (unit: IncrementUnit) => void;
  unitLabel: string;
  daysAvailable?: boolean;
}) {
  const { t } = useTranslation("booking");
  const factor = INCREMENT_UNIT_MINUTES[unit];
  return (
    <div className="flex flex-wrap items-center gap-2">
      <Input
        {...props}
        type="number"
        max={MAX_BOOKING_DURATION_MINUTES / factor}
        className="w-28"
        value={minutes === undefined ? "" : wholeMinutes(minutes / factor)}
        onChange={(event) =>
          onMinutesChange(
            event.currentTarget.value === "" ? undefined : wholeMinutes(event.currentTarget.valueAsNumber * factor),
          )
        }
      />
      <ButtonGroup aria-label={unitLabel}>
        {INCREMENT_UNITS.map((candidate) => {
          const unavailable = candidate === "DAYS" && !daysAvailable;
          return (
            <Button
              key={candidate}
              type="button"
              variant={unit === candidate ? "default" : "outline"}
              aria-pressed={unit === candidate}
              disabled={props.disabled || unavailable}
              focusableWhenDisabled={unavailable && !props.disabled}
              aria-describedby={unavailable ? `${props.id}-days-unavailable` : undefined}
              title={unavailable ? t("settings.fields.incrementDaysUnavailable") : undefined}
              className="aria-disabled:cursor-not-allowed aria-disabled:opacity-50"
              onClick={() => {
                if (unavailable || candidate === unit) return;
                onMinutesChange(
                  minutes === undefined
                    ? undefined
                    : wholeMinutes((minutes / factor) * INCREMENT_UNIT_MINUTES[candidate]),
                );
                onUnitChange(candidate);
              }}
            >
              {t(UNIT_LABEL_KEYS[candidate])}
            </Button>
          );
        })}
      </ButtonGroup>
      {daysAvailable ? null : (
        <span id={`${props.id}-days-unavailable`} className="sr-only">
          {t("settings.fields.incrementDaysUnavailable")}
        </span>
      )}
    </div>
  );
}

/** Choose the largest exact elapsed unit; zero follows the increment's unit. */
export function durationUnit(minutes: number, zeroUnit: IncrementUnit): IncrementUnit {
  if (minutes === 0) return zeroUnit;
  return minutes % 1440 === 0 ? "DAYS" : minutes % 60 === 0 ? "HOURS" : "MINUTES";
}
