//@flow

import axios from "axios";
import JwtService from "./JwtService";
import React from "react";

export default function useOauthToken(): {| getToken: () => Promise<string> |} {
  const [token, setToken] = React.useState<null | string>(null);

  async function fetchToken(): Promise<string> {
    const response = await axios.get<{| data: string |}>(
      "/userform/ajax/inventoryOauthToken"
    );
    const newToken = response.data.data;
    JwtService.saveToken(newToken);
    return newToken;
  }

  async function refreshToken(): Promise<void> {
    // we get a new token, memoise it for the duration of the window in which
    // the token is valid, and then trigger a recursive call to get another
    const newToken = await fetchToken();
    setToken(newToken);
    setTimeout(() => {
      void refreshToken();
    }, JwtService.secondsToExpiry(newToken) * 1000);
  }

  return {
    getToken: async () => {
      // use the memoised token, if we've previously called this hook
      if (token) return token;

      // otherwise, get a token: either from session storage or from the API
      const savedToken = JwtService.getToken() ?? (await fetchToken());
      setToken(savedToken);

      // before the token expires, refresh it
      // it is important that this setTimeout is set up regardless of whether
      // the token was got from the API or from session storage. As the user
      // navigates around and refreshes the page throughout the window in which
      // the token is valid, we want to keep setting up the refresh logic so
      // that when the token expires we get a new one, put it in session
      // storage, and the current page and all subsequent ones in the next
      // window will continue to work
      setTimeout(() => {
        void refreshToken();
      }, JwtService.secondsToExpiry(savedToken) * 1000);

      return savedToken;
    },
  };
}
