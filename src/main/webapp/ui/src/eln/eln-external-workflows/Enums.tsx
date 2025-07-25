const ErrorReason = {
  None: -1,
  NetworkError: 0,
  APIVersion: 1,
  NotFound: 2,
  Unauthorized: 3,
  Timeout: 4,
  BadRequest: 5,
  UNKNOWN: 6,
};
Object.freeze(ErrorReason);

export { ErrorReason };
