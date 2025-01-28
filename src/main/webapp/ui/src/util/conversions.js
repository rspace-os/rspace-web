// @flow
import { match } from "./Util";
import {
  type TemperatureScale,
  CELSIUS,
  KELVIN,
  ABSOLUTE_ZERO,
} from "../stores/definitions/Units";
import { type Temperature } from "../stores/definitions/Sample";
import Result from "./result";
import * as Parsers from "./parsers";

/* quantity conversions */

export const unitlessIds = {
  items: 1,
};

export const volumeIds = {
  microliters: 2,
  milliliters: 3,
  liters: 4,
  picoliters: 18,
  nanoliters: 19,
  millimeterscubed: 23,
  centimeterscubed: 24,
  decimeterscubed: 25,
  meterscubed: 26,
};

export const massIds = {
  micrograms: 5,
  milligrams: 6,
  grams: 7,
  picograms: 20,
  nanograms: 21,
  kilograms: 22,
};

export const quantityIds = {
  ...volumeIds,
  ...massIds,
  ...unitlessIds,
};

export type QuantityValue = number;
export type QuantityUnitId = $Values<typeof quantityIds>;

const isVolume = (q: QuantityUnitId) => Object.values(volumeIds).includes(q);

const isMass = (q: QuantityUnitId) => Object.values(massIds).includes(q);

const isUnitless = (q: QuantityUnitId) =>
  Object.values(unitlessIds).includes(q);

const atomicUnitOfSameCategory = (q: QuantityUnitId) =>
  match<void, () => QuantityUnitId>([
    [() => isVolume(q), () => quantityIds.picoliters],
    [() => isMass(q), () => quantityIds.picograms],
    [() => isUnitless(q), () => quantityIds.items],
    [
      () => true,
      () => {
        throw new Error(`Unknown unit: ${q}`);
      },
    ],
  ])()();

const quantityUnitMagnitudes = {
  [unitlessIds.items]: 0,
  [volumeIds.picoliters]: 0,
  [volumeIds.nanoliters]: 1,
  [volumeIds.microliters]: 2,
  [volumeIds.milliliters]: 3,
  [volumeIds.liters]: 4,
  [volumeIds.millimeterscubed]: 2,
  [volumeIds.centimeterscubed]: 3,
  [volumeIds.decimeterscubed]: 4,
  [volumeIds.meterscubed]: 5,
  [massIds.picograms]: 0,
  [massIds.nanograms]: 1,
  [massIds.micrograms]: 2,
  [massIds.milligrams]: 3,
  [massIds.grams]: 4,
  [massIds.kilograms]: 5,
};

export function toCommonUnit(value: QuantityValue, id: QuantityUnitId): number {
  const baseId = atomicUnitOfSameCategory(id);
  const gap = quantityUnitMagnitudes[id] - quantityUnitMagnitudes[baseId];
  return value * Math.pow(1000, gap);
}

export function fromCommonUnit(
  value: QuantityValue,
  id: QuantityUnitId
): number {
  const baseId = atomicUnitOfSameCategory(id);
  const gap = quantityUnitMagnitudes[id] - quantityUnitMagnitudes[baseId];
  return value / Math.pow(1000, gap);
}

/* time conversions */

export function msToHMS(ms: number): string {
  let seconds = ms / 1000;
  const hours = parseInt(seconds / 3600);
  seconds = seconds % 3600;
  const minutes = parseInt(seconds / 60);
  seconds = seconds % 60;
  const hmsFormatted = `${hours < 10 ? "0" : ""}${hours}:${
    minutes < 10 ? "0" : ""
  }${minutes}:${seconds < 10 ? "0" : ""}${parseInt(seconds)}`;
  return hmsFormatted;
}

export function msToDays(ms: number): number {
  return ms / (1000 * 60 * 60 * 24);
}

type DatePrecision =
  | "year"
  | "month"
  | "date"
  | "hour"
  | "minute"
  | "second"
  | "millisecond";

/*
 * This function outputs the prefix of a ISO timestamp.
 * The first argument is a Date object or any string that the JS runtime can
 * parse as a date (best to use ISO timestamp to play it safe). The second
 * argument specifies the length of the prefix based to the level of precision
 * that should be encoded in the output string.
 */
export function truncateIsoTimestamp(
  isoTimestamp: string | Date,
  precision: DatePrecision
): Result<string> {
  const date = new Date(isoTimestamp);
  if (date.toString() === "Invalid Date")
    return Result.Error([new Error("Invalid Date")]);
  let output = "";
  switch (precision) {
    case "millisecond":
      output = `.${date.getMilliseconds().toString().padStart(3, "0")}`;
    // falls through
    case "second":
      output = `:${date.getSeconds().toString().padStart(2, "0")}${output}`;
    // falls through
    case "minute":
      output = `:${date.getMinutes().toString().padStart(2, "0")}${output}`;
    // falls through
    case "hour":
      output = `T${date.getHours().toString().padStart(2, "0")}${output}`;
    // falls through
    case "date":
      output = `-${date.getDate().toString().padStart(2, "0")}${output}`;
    // falls through
    case "month":
      output = `-${(date.getMonth() + 1).toString().padStart(2, "0")}${output}`;
    // falls through
    case "year":
      output = `${date.getFullYear()}${output}`;
  }
  return Result.Ok(output);
}

export function todaysDate(): Date {
  return new Date(
    truncateIsoTimestamp(new Date(), "date").orElseGet(() => {
      throw new Error("Impossible");
      // `new Date()` can't produce an invalid date
    })
  );
}

/**
 * Get the relative time from now to a target date, in terms of the largest
 * unit of time that is smaller than the interval.
 *
 * @param targetDate The date in the future.
 */
export function getRelativeTime(targetDate: Date): string {
  const now = new Date();
  const futureDate = targetDate;
  //const diffInSeconds = Math.floor((futureDate - now) / 1000);
  const diffInSeconds = Math.floor(
    (futureDate.getTime() - now.getTime()) / 1000
  );

  const units = [
    { name: "year", seconds: 60 * 60 * 24 * 365 },
    { name: "month", seconds: 60 * 60 * 24 * 30 },
    { name: "day", seconds: 60 * 60 * 24 },
    { name: "hour", seconds: 60 * 60 },
    { name: "minute", seconds: 60 },
    { name: "second", seconds: 1 },
  ];

  for (const unit of units) {
    if (Math.abs(diffInSeconds) >= unit.seconds) {
      const value = Math.floor(diffInSeconds / unit.seconds);
      //$FlowExpectedError[prop-missing] -- Flow doesn't know about Intl.RelativeTimeFormat
      return new Intl.RelativeTimeFormat("en", { numeric: "auto" }).format(
        value,
        unit.name
      );
    }
  }

  return "now";
}

/* temperature conversions */

export function temperatureFromTo(
  from: TemperatureScale,
  to: TemperatureScale,
  value: number
): number {
  // prettier-ignore
  const valueInCelsius =
    from === CELSIUS     ? value            :
    from === KELVIN      ? value - 273.15   :
    /* else FAHRENHEIT */ (value - 32) / 1.8;
  // prettier-ignore
  const valueInNewUnit = Math.round(
    to === CELSIUS      ? valueInCelsius           :
    to === KELVIN       ? valueInCelsius + 273.15  :
    /* else FAHRENHEIT */ valueInCelsius * 1.8 + 32
  );
  return valueInNewUnit;
}

export const validateTemperature = (t: ?Temperature): Result<null> =>
  Result.first(
    !t ? Result.Ok(null) : Result.Error<null>([]),
    Parsers.isNotBottom(t)
      .flatMap((t) =>
        Parsers.isNotNaN(t.numericValue).map((numericValue) => ({
          numericValue,
          ...t,
        }))
      )
      .mapError(() => new Error("Temperature is invalid"))
      .flatMap((t) =>
        t.numericValue >= temperatureFromTo(CELSIUS, t.unitId, ABSOLUTE_ZERO)
          ? Result.Ok(null)
          : Result.Error([new Error("Temperature is less than absolute zero")])
      )
  );
