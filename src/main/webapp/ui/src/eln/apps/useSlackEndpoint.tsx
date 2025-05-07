import axios from "@/common/axios";

const ONE_MINUTE_IN_MS = 60 * 60 * 1000;

/**
 * There is an API endpoint that allows the UI to trigger the authentication
 * mechanism that allows the user to choose a Slack channel.
 */
export function useSlackEndpoint(): {
  oauthUrl: () => Promise<string>;
} {
  const api = axios.create({
    baseURL: "/slack",
    timeout: ONE_MINUTE_IN_MS,
  });

  const oauthUrl = async (): Promise<string> => {
    const response = await api.get<{
      success: true;
      data: string;
      error: null;
    }>("/oauthUrl");
    return response.data.data;
  };

  return { oauthUrl };
}
