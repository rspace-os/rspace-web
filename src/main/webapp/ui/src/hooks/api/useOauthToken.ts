import axios from "@/common/axios";
import JwtService from "../../common/JwtService";
import React from "react";

/**
 * This custom hook allows us to get a token for making calls to the API
 * endpoints that expect an API key. This started out being for Inventory
 * (hence the name of the /userform/ajax/inventoryOauthToken endpoint) but is
 * increasingly used for more and more of the product as we aim to expose more
 * of the functionality to third-parties via an open API.
 *
 * This code makes a request to the aforementioned endpoint using the user's
 * already established authenticated session based on cookies and in return
 * gets a token that can thereafter be used to make requests to the API
 * endpoints that expect an API key. To do so, do something akin to the
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
 * Don't worry about making execessive calls to `getToken` as the value is both
 * cached by this custom hook and by the session storage so that even on page
 * refresh calls will not be made to the endpoint. If several calls are made
 * whilst an initial network request is in flight then multiple API calls will
 * be made but this isn't generally an issue.
 *
 * This hook also automatically refreshes the token when it expires, so be sure
 * to not cache the value returned by `getToken` in the components or hooks
 * that use this hook as otherwise they will fail to work when the token
 * elapses.  If the page is loaded and there is a valid token in the session
 * storage then it is used and a timer is set up to refresh the token when it
 * expires just as if the token and been fetched directly.
 */
export default function useOauthToken(): { getToken: () => Promise<string> } {
  /*
   * We memoise the token, even if all we've done is pull it from the session
   * storage, so that every call to `getToken` does not set up a new
   * `setTimeout`.  The first call to this hook will get the token either from
   * the API endpoint or from the session storage and set up the logic to
   * ensure that a new token is fetched when that one expires, and all
   * subsequent calls will simply get it from this variable.
   */
  const [token, setToken] = React.useState<null | string>(null);

  /*
   * As part of fetching a new token, we save it into session storage so that
   * after the page is reloaded we can just get it from there. This is mostly
   * for performance: the reasoning being that the content of the page cannot
   * be loaded until we have a token so rather than having all of the content
   * wait on a single blocking network call we can just persist the value
   * across page loads and only incur that penalty on the first page load.
   */
  async function fetchToken(): Promise<string> {
    const response = await axios.get<{ data: string }>(
      "/userform/ajax/inventoryOauthToken",
    );
    const newToken = response.data.data;
    JwtService.saveToken(newToken);
    return newToken;
  }

  /*
   * When the token is about to expire, this function is called which
   * recursively ensures that a new token is fetched. We also memoise that new
   * token so that subsequent calls to `getToken` will use the new one.
   */
  async function refreshToken(): Promise<void> {
    const newToken = await fetchToken();
    setToken(newToken);
    setTimeout(
      () => {
        void refreshToken();
      },
      JwtService.secondsToExpiry(newToken) * 1000,
    );
  }

  return {
    getToken: async () => {
      /*
       * If this hook has been called previously and we've either already
       * fetched a token or pulled it from the session storage, then we can
       * just return it; thereby ensuring that we don't setup a new
       * `setTimeout` with each call to `getToken`.
       */
      if (token) return token;

      /*
       * If this hook has not been previously called then we preferably get the
       * token from session storage or else get it from the API endpoint.
       */
      const savedToken = JwtService.getToken() ?? (await fetchToken());
      setToken(savedToken);

      /*
       * Whether the token is fetched from the API or got from the session
       * storage, we set up a timer to trigger the continuous refreshing of the
       * token. As the user navigates around and refreshes the page throughout
       * the window in which the token is valid, we want to keep setting up the
       * refresh logic so that when the token expires we get a new one, put it
       * in session storage, and the current page and all subsequent ones in
       * the next temporal window will continue to work.
       */
      setTimeout(
        () => {
          void refreshToken();
        },
        JwtService.secondsToExpiry(savedToken) * 1000,
      );
      return savedToken;
    },
  };
}
