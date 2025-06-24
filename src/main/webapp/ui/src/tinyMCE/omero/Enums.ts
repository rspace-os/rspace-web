/**
 * Enum of possible error reasons
 */
const ErrorReason = {
  None: -1,
  NetworkError: 0,
  APIVersion: 1,
  NotFound: 2,
  Unauthorized: 3,
  Timeout: 4,
  BadRequest: 5,
  UNKNOWN: 6,
} as const;

/**
 * Sort order enum
 */
const Order = {
  asc: "asc",
  desc: "desc",
} as const;

Object.freeze(ErrorReason);
Object.freeze(Order);

export type ErrorReasonType = (typeof ErrorReason)[keyof typeof ErrorReason];
export type OrderType = (typeof Order)[keyof typeof Order];

export { ErrorReason, Order };
