import axios from "@/common/axios";
import { type OptionsId } from "./useIntegrationsEndpoint";

const ONE_MINUTE_IN_MS = 60 * 60 * 1000;

/**
 * There is an API endpoint that allows the UI to provide the user with a
 * mechanism for checking that the connection details they have provided are
 * valid.
 */
export function useDataverseTestEndpoint(): {
  /**
   * Test that the saved configuration is valid.
   */
  test: (optionsId: OptionsId) => Promise<void>;
} {
  const api = axios.create({
    baseURL: "/repository/ajax/testRepository",
    timeout: ONE_MINUTE_IN_MS,
  });

  const test = async (optionsId: OptionsId): Promise<void> => {
    const response = await api.get<
      string | { errorId: string; exceptionMessage: string; tstamp: string }
    >(optionsId);
    if (typeof response.data === "object") {
      throw new Error(response.data.exceptionMessage);
    }
    if (!/^Success/.test(response.data)) {
      throw new Error(response.data);
    }
  };

  return { test };
}
