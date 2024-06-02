// @flow strict

/*
 * Types and utility functions for calculating and displaying the progress of
 * the some asynchronous process.
 */

/*
 * Progress is a number between 0 and 100, inclusive, rounded to a single
 * decimal place. This invariant is enforced by the fact that it is opaque, so
 * other modules can only create and use an instance of the type via the
 * functions exported by this module.
 */
export opaque type Progress = number;

export function calculateProgress({
  progressMade,
  total,
}: {|
  progressMade: number,
  total: number,
|}): Progress {
  if (progressMade < 0)
    throw new TypeError(
      "progressMade must either be zero or a positive number."
    );
  if (total <= 0) throw new TypeError("total must be a positive number.");
  if (total < progressMade)
    throw new TypeError("total must be greater than or equal to progressMade.");
  return Math.floor((progressMade / total) * 10) * 10;
}

export function asPercentageString(progress: Progress): string {
  return `${progress}%`;
}

export const noProgress: Progress = 0;
export const complete: Progress = 100;

/*
 * Some helper functions for use in setting aria- attributes.
 */
export function ariaValueNow(progress: Progress): number {
  return progress;
}

export function ariaValueMin(): number {
  return noProgress;
}

export function ariaValueMax(): number {
  return complete;
}
