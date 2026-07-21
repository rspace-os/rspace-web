export const FEATURE_FLAGS_API_BASE_URL = "/api/v2/feature-flags";

export const featureFlagRequestHeaders = (token?: string | null): Record<string, string> => ({
  "X-Requested-With": "XMLHttpRequest",
  ...(token ? { Authorization: `Bearer ${token}` } : {}),
});

export const toFeatureFlagRequestError = (response: Response): Error =>
  new Error(`${response.status} ${response.statusText}`.trim());
