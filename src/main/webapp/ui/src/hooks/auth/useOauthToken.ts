import React from "react";
import axios from "@/common/axios";
import {
  fetchOauthToken,
  isPublicDocumentView,
  mintUiToken,
  saveStoredToken,
  tokenRefreshDelayMs,
} from "@/modules/common/utils/auth";

/**
 * This custom hook allows us to get a token for making calls to the API
 * endpoints that expect an API key. This started out being for Inventory
 * (hence the name of the /userform/ajax/inventoryOauthToken endpoint) but is
 * increasingly used for more and more of the product as we aim to expose more
 * of the functionality to third-parties via an open API.
 *
 * Authenticated pages mint a REST API v2 UI token from the user's existing
 * browser session. Public document pages retain the legacy token endpoint.
 * The returned token can then be used to make requests to API endpoints that
 * expect a bearer token. To do so, do something akin to the
 * following:
 *
 *   const api = axios.create({
 *     baseURL: `/api/v1/${PART_OF_API}`,
 *     headers: {
 *       Authorization: "Bearer " + (await getToken()),
 *     },
 *   });
 *   await api.get(ENDPOINT);
 *
 * Don't worry about making excessive calls to `getToken`: the value is cached
 * by this hook, and overlapping initial requests are coalesced.
 *
 * This hook also automatically refreshes the token shortly before it expires, so be sure
 * to not cache the value returned by `getToken` in the components or hooks
 * that use this hook as otherwise they will fail to work when the token
 * elapses. Authenticated pages mint a fresh token on each page load so run-as
 * changes cannot reuse an earlier browser-session context. The refresh timer
 * is cancelled when the component using this hook unmounts.
 */
export default function useOauthToken(): { getToken: () => Promise<string> } {
  /*
   * We use a ref instead of useState to store the token because we need the
   * `getToken` function to have a stable reference. If we used useState, then
   * every time the token changes, the `getToken` useCallback would be
   * recreated (since it would depend on the token state). This would cause
   * any components using `getToken` to recreate their own useCallbacks that
   * depend on it, leading to duplicate API calls as useEffects re-run.
   *
   * With a ref, the token value can change without affecting the stability of
   * the `getToken` function reference, preventing this cascade of re-renders
   * and duplicate network requests.
   */
  const tokenRef = React.useRef<string | null>(null);
  const refreshTimerRef = React.useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const unmountedRef = React.useRef(false);

  /*
   * On unmount, cancel the pending refresh and forget the token. Forgetting it
   * matters under StrictMode, which unmounts and remounts straight away: the
   * next `getToken` then starts a new timer instead of returning a token that
   * no longer has one. A refresh still in flight at unmount schedules nothing.
   */
  React.useEffect(() => {
    unmountedRef.current = false;
    return () => {
      unmountedRef.current = true;
      clearTimeout(refreshTimerRef.current);
      tokenRef.current = null;
    };
  }, []);

  /*
   * Each page context mints a token after load so a public page cannot reuse an
   * authenticated page's session-bound token from shared session storage.
   */
  async function fetchToken(): Promise<string> {
    if (isPublicDocumentView()) {
      const response = await axios.get<{ data: string }>("/public/publicView/userform/ajax/inventoryOauthToken");
      const newToken = response.data.data;
      saveStoredToken(newToken);
      return newToken;
    }
    return mintUiToken();
  }

  /*
   * When the token is about to expire, this function is called which
   * recursively ensures that a new token is fetched. We also memoise that new
   * token so that subsequent calls to `getToken` will use the new one.
   */
  async function refreshToken(): Promise<void> {
    const newToken = await (isPublicDocumentView() ? fetchOauthToken(fetchToken) : mintUiToken());
    if (unmountedRef.current) return;
    tokenRef.current = newToken;
    scheduleRefresh(newToken);
  }

  /*
   * If a refresh fails, usually because the session has ended, the token is
   * forgotten so that the next call to `getToken` mints a fresh token
   * from the endpoint and sets up a new timer.
   */
  function scheduleRefresh(token: string): void {
    clearTimeout(refreshTimerRef.current);
    refreshTimerRef.current = setTimeout(() => {
      refreshToken().catch(() => {
        tokenRef.current = null;
      });
    }, tokenRefreshDelayMs(token));
  }

  const getToken = React.useCallback(async () => {
    /*
     * If this hook has been called previously and we've either already
     * fetched a token for this page context, then we can
     * just return it; thereby ensuring that we don't setup a new
     * `setTimeout` with each call to `getToken`.
     */
    if (tokenRef.current) return isPublicDocumentView() ? tokenRef.current : mintUiToken();

    /*
     * Mint a token for the current page context instead of reusing the shared
     * session-storage token, which may belong to another browser-session context.
     */
    const initialToken = await (isPublicDocumentView() ? fetchOauthToken(fetchToken) : mintUiToken());
    if (unmountedRef.current) return initialToken;
    tokenRef.current = initialToken;
    scheduleRefresh(initialToken);
    return initialToken;
  }, []);

  return {
    getToken,
  };
}
