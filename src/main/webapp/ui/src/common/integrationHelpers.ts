/*
 * This file contains helper functions for working with third-party
 * integrations.
 */

import axios from "@/common/axios";
import * as FetchingData from "../util/fetchingData";
import { doNotAwait } from "../util/Util";
import React from "react";
import * as Parsers from "../util/parsers";
import Result from "../util/result";

/**
 * A title-case name for displaying in the UI.
 */
export type IntegrationDisplayName = string;

/**
 * An uppercase name that uniquely identifies the integration.
 */
export type IntegrationName = string;

/**
 * The current status of a particular integration
 */
export type IntegrationInfo = {
  available: boolean;
  displayName: IntegrationDisplayName;
  enabled: boolean;
  name: IntegrationName;
  oauthConnected: boolean;
  options: object;
};

/**
 * A simple function for fetching information on the status of a particular
 * integration. Most notably, it can be used to determine if the sysadmin has
 * made the integration available, whether the user has enabled it, and
 * whether they are authenticated (if applicable).
 */
export async function fetchIntegrationInfo(
  name: IntegrationName
): Promise<IntegrationInfo> {
  const { data } = await axios.get<{ data: IntegrationInfo }>(
    "/integration/integrationInfo",
    {
      params: new URLSearchParams({ name }),
      responseType: "json",
    }
  );
  return Parsers.isObject(data)
    .flatMap(Parsers.isNotNull)
    .flatMap(Parsers.getValueWithKey("data"))
    .flatMap(Parsers.isObject)
    .flatMap(Parsers.isNotNull)
    .flatMap((obj) => {
      try {
        const available = Parsers.getValueWithKey("available")(obj)
          .flatMap(Parsers.isBoolean)
          .elseThrow();
        const displayName = Parsers.getValueWithKey("displayName")(obj)
          .flatMap(Parsers.isString)
          .elseThrow();
        const enabled = Parsers.getValueWithKey("enabled")(obj)
          .flatMap(Parsers.isBoolean)
          .elseThrow();
        const name = Parsers.getValueWithKey("name")(obj)
          .flatMap(Parsers.isString)
          .elseThrow();
        const oauthConnected = Parsers.getValueWithKey("oauthConnected")(obj)
          .flatMap(Parsers.isBoolean)
          .elseThrow();
        return Result.Ok({
          available,
          displayName,
          enabled,
          name,
          oauthConnected,
          options: {},
        });
      } catch (e) {
        if (e instanceof Error) return Result.Error<IntegrationInfo>([e]);
        return Result.Error<IntegrationInfo>([new Error("Unknown error")]);
      }
    })
    .elseThrow();
}

/**
 * When a particular piece of UI -- a button, a menu item, etc -- should only
 * be visible when the sysadmin has allowed the integration and the user has
 * enabled it on the apps page, this custom hook should be used to
 * conditionally render that UI element.
 *
 * It does not check whether the user is authenticated as whether the user
 * needs to be authenticated and the mechanism by which they do so vary between
 * the different integrations so abstrating over them all is tricky. Generally,
 * where the button or menu item in question opens a dialog, the content of the
 * dialog itself determines whether the user is authenticated based on the
 * response to an authenticated endpoint and presents an inline alert based on
 * the response when they are not.
 *
 * Do note that this custom hook will fetch the integration's state on every
 * mount so if the menu item is unmounted and remounted when the menu is opened
 * and closed then the integration state will be fetched over and over. This is
 * not a huge performance issue but it may be desirable to add the
 * `keepMounted` prop to the Menu to prevent this, see
 * https://mui.com/material-ui/api/modal/#modal-prop-keepMounted
 */
export function useIntegrationIsAllowedAndEnabled(
  name: IntegrationName
): FetchingData.Fetched<boolean> {
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState("");
  const [integrationState, setIntegrationState] =
    React.useState<null | IntegrationInfo>(null);

  React.useEffect(
    doNotAwait(async () => {
      try {
        setIntegrationState(await fetchIntegrationInfo(name));
      } catch (e) {
        if (e instanceof Error) setError(e.message);
      } finally {
        setLoading(false);
      }
    }),
    []
  );

  if (loading) return { tag: "loading" };
  if (integrationState === null) return { tag: "error", error };
  return {
    tag: "success",
    value: integrationState.enabled && integrationState.available,
  };
}
