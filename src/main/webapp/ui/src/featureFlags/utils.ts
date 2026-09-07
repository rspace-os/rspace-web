export const FEATURE_FLAGS_API_BASE_URL = "/api/v2/feature-flags";

export const featureFlagRequestHeaders = (token?: string | null): Record<string, string> => ({
  "X-Requested-With": "XMLHttpRequest",
  ...(token ? { Authorization: `Bearer ${token}` } : {}),
});

export class FeatureFlagRequestError extends Error {
  constructor(
    readonly status: number,
    statusText: string,
  ) {
    super(`${status} ${statusText}`.trim());
  }
}

export const toFeatureFlagRequestError = (response: Response): FeatureFlagRequestError =>
  new FeatureFlagRequestError(response.status, response.statusText);
