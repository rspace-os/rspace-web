import { getUiToken } from "@/modules/common/utils/auth";

const getToken = (): Promise<string> => getUiToken();

/**
 * Gets a bearer token for API endpoints that expect one. This started out being for Inventory
 * (hence the name of the legacy /userform/ajax/inventoryOauthToken endpoint) but is increasingly
 * used for more of the product as we expose more of it to third parties via an open API.
 *
 * `getToken` reads the page-wide API v2 token provider, which mints a session-bound token on
 * authenticated pages and uses the legacy public endpoint on public document pages. The provider
 * caches the token, coalesces overlapping requests and refreshes the token before it expires, so
 * call `getToken` before each request rather than caching its result:
 *
 *   const api = axios.create({
 *     baseURL: `/api/v1/${PART_OF_API}`,
 *     headers: {
 *       Authorization: "Bearer " + (await getToken()),
 *     },
 *   });
 *   await api.get(ENDPOINT);
 */
export default function useOauthToken(): { getToken: () => Promise<string> } {
  return { getToken };
}
