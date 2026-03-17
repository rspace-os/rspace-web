import axios from "@/common/axios";

const ONE_MINUTE_IN_MS = 60 * 60 * 1000;

/**
 * There is no specific DSW API endpoint to test the status of a connection,
 * so to provide the ability to test from the UI whether connection details
 * are valid or not we simply try to retrieve details concerning the user
 * associated with the current set of connection credentials.
 */
export function useDSWTestEndpoint(): {
  /**
   * Test that the saved configuration is valid.
   */
  test: (serverAlias: string) => Promise<void>;
} {
  const api = axios.create({
    baseURL: "/apps/dsw",
    timeout: ONE_MINUTE_IN_MS,
  });

  const test = async (serverAlias: string): Promise<void> => {
    const response = await api.get<JSON>("/currentUser?serverAlias=" + serverAlias);

    if (null == response.data) {
      throw new Error("Response data was empty");
    }
  };

  return { test };
}
