//@flow

export const CELSIUS = 8;
export const KELVIN = 9;
export const FAHRENHEIT = 10;
export type TemperatureScale =
  | typeof CELSIUS
  | typeof KELVIN
  | typeof FAHRENHEIT;

// In celsius
export const ABSOLUTE_ZERO = -273;
export const LIQUID_NITROGEN = -196;
