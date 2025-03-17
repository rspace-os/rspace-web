import { match } from "../../util/Util";
import Result from "../../util/result";
import * as Parsers from "../../util/parsers";

/**
 * @module
 * @desc Scientists often record data in different units of measure. This module
 *       defines enums for these different types of units, types and objects for
 *       model quantities of those units, and functions for converting between.
 */

/**
 * First, enums for different categories of units of measure, and objects that model
 * some quantity of those units.
 */

/**
 * Enum value for quantities that do not have a unit and come in discrete
 * amounts e.g. rock samples. The associated QuantityValue should be a positive
 * integer.
 */
export const unitlessIds = {
  items: 1,
};

/**
 * Enum values for units of quantities measured in volume.
 */
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

/**
 * Enum values for units of quantities measured in mass.
 */
export const massIds = {
  micrograms: 5,
  milligrams: 6,
  grams: 7,
  picograms: 20,
  nanograms: 21,
  kilograms: 22,
};

/**
 * Enum values for all types of quantities.
 */
export const quantityIds = {
  ...volumeIds,
  ...massIds,
  ...unitlessIds,
};

/**
 * The numerical amount of a particular quantity.
 */
export type QuantityValue = number;

/**
 * The unit associated with a particular quantity.
 */
export type QuantityUnitId = (typeof quantityIds)[keyof typeof quantityIds];

/**
 * Checks where a quantity's unit is one that measures volume.
 */
const isVolume = (q: QuantityUnitId) => Object.values(volumeIds).includes(q);

/**
 * Checks where a quantity's unit is one that measures mass.
 */
const isMass = (q: QuantityUnitId) => Object.values(massIds).includes(q);

/**
 * Checks where a quantity's unit is one that comes in a discrete amount.
 */
const isUnitless = (q: QuantityUnitId) =>
  Object.values(unitlessIds).includes(q);

/**
 * For each category of quantity, there is a smallest unit of that category
 * that RSpace supports. This function returns that unit for any given unit of
 * the same category e.g. for volume, the smallest unit is picoliters.
 */
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

/**
 * For each unit, this object stores the power of 1000 by which the unit is
 * greater than the atomic unit of the same unit category e.g. grams are 1000^4 times greater than picograms.
 */
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

/**
 * Converts a quantity value from a given unit to the atomic unit of the same
 * category.
 * @arg value The quantity value to convert e.g. 4
 * @arg id    The unit to convert from e.g. massIds.grams
 * @returns   The converted quantity value e.g. 4,000,000,000
 *
 * Be very careful when using this function, as it can easily lead to values
 * that are greater than Number.MAX_SAFE_INTEGER.
 */
export function toCommonUnit(value: QuantityValue, id: QuantityUnitId): number {
  const baseId = atomicUnitOfSameCategory(id);
  const gap = quantityUnitMagnitudes[id] - quantityUnitMagnitudes[baseId];
  return value * Math.pow(1000, gap);
}

/**
 * Converts a quantity value from the atomic unit of the same category to a given
 * unit.
 * @arg value The quantity value to convert e.g. 4,000,000,000
 * @arg id    The unit to convert to e.g. massIds.grams
 * @returns   The converted quantity value e.g. 4
 */
export function fromCommonUnit(
  value: QuantityValue,
  id: QuantityUnitId
): number {
  const baseId = atomicUnitOfSameCategory(id);
  const gap = quantityUnitMagnitudes[id] - quantityUnitMagnitudes[baseId];
  return value / Math.pow(1000, gap);
}

/**
 * Specifics for working with dates and times
 */

/**
 * Converts a number of milliseconds to a number of days
 */
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

/**
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

/**
 * Gets today's date without the time component.
 */
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

  const units: Array<{ name: Intl.RelativeTimeFormatUnit; seconds: number }> = [
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
      return new Intl.RelativeTimeFormat("en", { numeric: "auto" }).format(
        value,
        unit.name
      );
    }
  }

  return "now";
}

/**
 * Specifics for working with temperatures
 */

/**
 * Enum value for degrees Celsius.
 */
export const CELSIUS = 8;

/**
 * Enum value for Kelvin.
 */
export const KELVIN = 9;

/**
 * Enum value for degrees Fahrenheit.
 */
export const FAHRENHEIT = 10;

/**
 * The type of any temerature scale.
 */
export type TemperatureScale =
  | typeof CELSIUS
  | typeof KELVIN
  | typeof FAHRENHEIT;

/**
 * A particular temperature, in a particular scale.
 */
export type Temperature = {
  numericValue: number;
  unitId: TemperatureScale;
};

/**
 * Absolute zero in degrees Celsius.
 */
export const ABSOLUTE_ZERO = -273;

/**
 * The boiling point of nitrogen in degrees Celsius. Useful in defining
 * temperatures in labs because samples are often stored in liquid nitrogen at
 * temperatures below this.
 */
export const LIQUID_NITROGEN = -196;

/**
 * Convert a temperature from one scale to another.
 */
export function temperatureFromTo(
  from: TemperatureScale,
  to: TemperatureScale,
  value: number
): number {
  let valueInCelsius = value;
  if (from === KELVIN) valueInCelsius = value - 273.15;
  else if (from === FAHRENHEIT) valueInCelsius = (value - 32) / 1.8;
  if (to === CELSIUS) return Math.round(valueInCelsius);
  if (to === KELVIN) return Math.round(valueInCelsius + 273.15);
  return Math.round(valueInCelsius * 1.8 + 32);
}

/**
 * If a non-null value is passed, then it is check to be not NaN and not less
 * than absolute zero.
 */
export const validateTemperature = (temp: Temperature | null): Result<null> =>
  Result.first(
    !temp ? Result.Ok(null) : Result.Error<null>([]),
    Parsers.isNotBottom(temp)
      .flatMapDiscarding((t) => Parsers.isNotNaN(t.numericValue))
      .mapError(() => new Error("Temperature is invalid"))
      .flatMap((t) =>
        t.numericValue >= temperatureFromTo(CELSIUS, t.unitId, ABSOLUTE_ZERO)
          ? Result.Ok(null)
          : Result.Error([new Error("Temperature is less than absolute zero")])
      )
  );
