//@flow strict

import axios from "axios";
import * as ArrayUtils from "../../util/ArrayUtils";

export type Repository = {|
  description: string,
  full_name: string,
|};

export type RepositoryListing = {|
  repositories: Array<Repository>,
  accessToken: string,
|};

const ONE_MINUTE_IN_MS = 60 * 60 * 1000;

/*
 * There is an API endpoint that allows the UI to fetch a list of all of the
 * user's GitHub repositories.
 */
export function useGitHubEndpoint(): {|
  getAllRepositories: (string) => Promise<Array<Repository>>,
  oauthUrl: () => Promise<string>,
|} {
  const api = axios.create({
    baseURL: "/github",
    timeout: ONE_MINUTE_IN_MS,
  });

  const getAllRepositories = async (
    authToken: string
  ): Promise<Array<Repository>> => {
    const response = await api.get<
      | {| success: true, data: Array<Repository>, error: null |}
      | {|
          success: false,
          data: null,
          errorMsg:
            | string
            | {|
                errorMessages: Array<string>,
              |},
        |}
    >("/allRepositories", { params: new URLSearchParams({ authToken }) });
    if (!response.data.success) {
      if (response.data.errorMsg) {
        const errorMsg = response.data.errorMsg;
        if (typeof errorMsg === "string") {
          throw new Error(errorMsg);
        } else {
          throw new Error(
            ArrayUtils.getAt(0, errorMsg.errorMessages).orElse("Unknown reason")
          );
        }
      }
    } else {
      return response.data.data;
    }
    throw new Error("Unknown reason");
  };

  const oauthUrl = async (): Promise<string> => {
    const response = await api.get<{|
      success: true,
      data: string,
      error: null,
    |}>("/oauthUrl");
    return response.data.data;
  };

  return { getAllRepositories, oauthUrl };
}
