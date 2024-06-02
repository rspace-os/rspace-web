//@flow

import axios from "axios";
import JwtService from "./JwtService";

export default function useOauthToken(): {| getToken: () => Promise<string> |} {
  async function fetchToken(): Promise<string> {
    const response = await axios.get<{| data: string |}>(
      "/userform/ajax/inventoryOauthToken"
    );
    const newToken = response.data.data;
    JwtService.saveToken(newToken);
    setTimeout(() => {
      void fetchToken();
    }, JwtService.secondsToExpiry(newToken) * 1000);
    return newToken;
  }

  return {
    getToken: (): Promise<string> => {
      const savedToken = JwtService.getToken();
      if (savedToken) {
        return Promise.resolve(savedToken);
      }
      return fetchToken();
    },
  };
}
