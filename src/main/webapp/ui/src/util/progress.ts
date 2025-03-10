/*
 * Types and utility functions for calculating and displaying the progress of
 * the some asynchronous process.
 */

/**
 * Progress is a number between 0 and 100, inclusive, rounded to a single
 * decimal place. Other modules MUST only create an instance by calling the
 * exported functions.
 */
export type Progress = number;

/**
 * Calcuate the progress. Constructor function for Progress.
 */
export function calculateProgress({
  progressMade,
  total,
}: {
  progressMade: number;
  total: number;
}): Progress {
  if (progressMade < 0)
    throw new TypeError(
      "progressMade must either be zero or a positive number."
    );
  if (total <= 0) throw new TypeError("total must be a positive number.");
  if (total < progressMade)
    throw new TypeError("total must be greater than or equal to progressMade.");
  return Math.floor((progressMade / total) * 10) * 10;
}

/**
 * Render the progress as a percentage string.
 */
export function asPercentageString(progress: Progress): string {
  return `${progress}%`;
}

/**
 * Constant of no progress.
 */
export const noProgress: Progress = 0;

/**
 * Constant of complete progress.
 */
export const complete: Progress = 100;

/*
 * Some helper functions for use in setting aria- attributes.
 */

/**
 * Helper for setting aria-valuenow.
 */
export function ariaValueNow(progress: Progress): number {
  return progress;
}

/**
 * Helper for setting aria-valuemin.
 */
export function ariaValueMin(): number {
  return noProgress;
}

/**
 * Helper for setting aria-valuemax.
 */
export function ariaValueMax(): number {
  return complete;
}
