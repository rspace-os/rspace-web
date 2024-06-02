//@flow

/*
 * This file contains helper functions for working with third-party
 * integrations.
 */

import axios from "axios";

// A title-case name for displaying in the UI.
export type IntegrationDisplayName = string;

// An uppercase name that uniquely identifies the integration.
export type IntegrationName = string;

export type IntegrationInfo = {|
  available: boolean,
  displayName: IntegrationDisplayName,
  enabled: boolean,
  name: IntegrationName,
  oauthConnected: boolean,
  options: {},
|};

/*
 * A simple function for fetching information on the status of a particular
 * integration. Most notably, it can be used to determine if the sysadmin has
 * made the integration available, whether the user has enabled it, and
 * whether they are authenticated (if applicable).
 */
export async function fetchIntegrationInfo(
  name: IntegrationName
): Promise<IntegrationInfo> {
  const { data } = await axios.get<{| data: IntegrationInfo |}>(
    "/integration/integrationInfo",
    {
      params: new URLSearchParams({ name }),
      responseType: "json",
    }
  );
  return data.data;
}
