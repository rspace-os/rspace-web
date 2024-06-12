//@flow strict

import React, { useContext } from "react";
import axios from "axios";
import { getByKey, Optional } from "../../util/optional";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import * as ArrayUtils from "../../util/ArrayUtils";
import { parseString } from "../../util/parsers";
import Result from "../../util/result";

/*
 * This module provide the functionality for interacting with the
 * /integrations/* API endpoint. It exposes a function for calling the
 * /integrations/allIntegrations to get the current state of all of the
 * integrations, and others functions for modifying those states.
 */

/**
 * Whilst this is string, it will always the decimal representation of a
 * natural number. The values are created by the server, and are simply passed
 * around by the frontend as handles to pass to API calls.
 */
export type OptionsId = string;

/**
 * This type models the current state of a particular integration. It is
 * polymorphic over the type of the credentials so that each integration can
 * accurately model the particular credentials it requires.
 */
export type IntegrationState<Credentials> = {|
  mode: "UNAVAILABLE" | "DISABLED" | "ENABLED",
  credentials: Credentials,
|};

/**
 * This type maps integrations to their current state.
 *
 * All integrations are in one of the three modes: either
 *  - they are unavailable because the sysadmin has not allowed their use,
 *  - they are disabled because the user has not chosen to use them, or
 *  - they are enabled and ready for use.
 * In each case, there may or may not also be a set of credentials stored with
 * each integration, the type of which varies depending upon how the
 * authentication with the integration works.
 *
 * The UI should only allow the user to enable an integration that requires a
 * credential when it has been provided, but we model the credentials as an
 * Optional anyway because it keeps the code uniform across each of the three
 * modes and ensures that the code handles the possibility that the backend
 * does not uphold this invariant. Do note that an unavailable integration may
 * have an associated credential because it may have been available previously.
 */
export type IntegrationStates = {|
  ARGOS: IntegrationState<{||}>,
  BOX: IntegrationState<{|
    BOX_LINK_TYPE: Optional<"LIVE" | "VERSIONED" | "ASK">,
    "box.api.enabled": Optional<boolean>,
  |}>,
  CHEMISTRY: IntegrationState<{||}>,
  CLUSTERMARKET: IntegrationState<{|
    ACCESS_TOKEN: Optional<string>,
  |}>,
  DATAVERSE: IntegrationState<
    Array<
      Optional<{|
        DATAVERSE_APIKEY: string,
        DATAVERSE_URL: string,
        DATAVERSE_ALIAS: string,
        _label: string,
        optionsId: OptionsId,
      |}>
    >
  >,
  DMPONLINE: IntegrationState<{||}>,
  DMPTOOL: IntegrationState<{|
    ACCESS_TOKEN: Optional<string>,
  |}>,
  DROPBOX: IntegrationState<{||}>,
  DRYAD: IntegrationState<{|
    ACCESS_TOKEN: Optional<string>,
  |}>,
  EGNYTE: IntegrationState<{|
    EGNYTE_DOMAIN: Optional<string>,
  |}>,
  EVERNOTE: IntegrationState<{||}>,
  FIGSHARE: IntegrationState<{|
    ACCESS_TOKEN: Optional<string>,
  |}>,
  GITHUB: IntegrationState<
    Array<
      Optional<{|
        /*
         * we use an inner Optional so that the user can see which repo is in an
         * invalid state, and can thus remove and readd
         */
        GITHUB_ACCESS_TOKEN: Optional<string>,
        GITHUB_REPOSITORY_FULL_NAME: string,
        optionsId: OptionsId,
      |}>
    >
  >,
  GOOGLEDRIVE: IntegrationState<{|
    ["googledrive.linking.enabled"]: Optional<boolean>,
  |}>,
  JOVE: IntegrationState<{||}>,
  MENDELEY: IntegrationState<{||}>,
  MSTEAMS: IntegrationState<
    Array<
      Optional<{|
        MSTEAMS_CHANNEL_LABEL: string,
        MSTEAMS_WEBHOOK_URL: string,
        optionsId: OptionsId,
      |}>
    >
  >,
  NEXTCLOUD: IntegrationState<{|
    ACCESS_TOKEN: Optional<string>,
  |}>,
  OMERO: IntegrationState<{||}>,
  ONEDRIVE: IntegrationState<{||}>,
  OWNCLOUD: IntegrationState<{|
    ACCESS_TOKEN: Optional<string>,
  |}>,
  PROTOCOLS_IO: IntegrationState<{|
    ACCESS_TOKEN: Optional<string>,
  |}>,
  PYRAT: IntegrationState<{|
    PYRAT_USER_TOKEN: Optional<string>,
  |}>,
  SLACK: IntegrationState<
    Array<
      Optional<{|
        SLACK_TEAM_NAME: string,
        SLACK_CHANNEL_ID: string,
        SLACK_CHANNEL_NAME: string,
        SLACK_USER_ID: string,
        SLACK_CHANNEL_LABEL: string,
        SLACK_USER_ACCESS_TOKEN: string,
        SLACK_TEAM_ID: string,
        SLACK_WEBHOOK_URL: string,
        optionsId: OptionsId,
      |}>
    >
  >,
  ZENODO: IntegrationState<{|
    ZENODO_USER_TOKEN: Optional<string>,
  |}>,
|};

type FetchedState = {
  name: string,
  available: boolean,
  enabled: boolean,
  options: { ... },
  ...
};

/**
 * This converts the model that the backend has into the model that the
 * frontend has. Java and JSON don't support unions so the backend has to
 * use two booleans, but with JS and Flow we can make the fourth possibility
 * (where is unavailable but enabled) impossible to model.
 */
function parseState({
  enabled,
  available,
}: FetchedState): IntegrationState<mixed>["mode"] {
  if (!available) return "UNAVAILABLE";
  if (!enabled) return "DISABLED";
  return "ENABLED";
}

/**
 * Checks that `options` has a key-value pair with the key `key`, and if it
 * does that that value is a string. If so, then the value is returned wrapped
 * in Optional.present, otherwise Optional.empty is returned.
 */
function parseCredentialString(
  options: { ... },
  key: string
): Optional<string> {
  return getByKey(key, options).flatMap((cred) =>
    typeof cred === "string" ? Optional.present(cred) : Optional.empty()
  );
}

/**
 * Checks that `options` has a key-value pair with the key `key`, and if it
 * does that that value is a boolean. If so, then the value is returned wrapped
 * in Optional.present, otherwise Optional.empty is returned.
 */
function parseCredentialBoolean(
  options: { ... },
  key: string
): Optional<boolean> {
  return getByKey(key, options).flatMap((cred) =>
    typeof cred === "boolean" ? Optional.present(cred) : Optional.empty()
  );
}

export type Integration = $Keys<IntegrationStates>;

function decodeArgos(data: FetchedState): IntegrationStates["ARGOS"] {
  return { mode: parseState(data), credentials: {} };
}

function decodeBox(data: FetchedState): IntegrationStates["BOX"] {
  return {
    mode: parseState(data),
    credentials: {
      BOX_LINK_TYPE: getByKey("BOX_LINK_TYPE", data.options)
        .flatMap((option) =>
          typeof option === "string"
            ? Optional.present(option)
            : Optional.empty()
        )
        .flatMap((option) =>
          Result.first(
            (parseString("LIVE", option): Result<"LIVE" | "VERSIONED" | "ASK">),
            (parseString("VERSIONED", option): Result<
              "LIVE" | "VERSIONED" | "ASK"
            >),
            (parseString("ASK", option): Result<"LIVE" | "VERSIONED" | "ASK">)
          ).toOptional()
        ),
      ["box.api.enabled"]: parseCredentialBoolean(
        data.options,
        "box.api.enabled"
      ),
    },
  };
}

function decodeChemistry(data: FetchedState): IntegrationStates["CHEMISTRY"] {
  return {
    mode: parseState(data),
    credentials: {},
  };
}

function decodeClustermarket(
  data: FetchedState
): IntegrationStates["CLUSTERMARKET"] {
  return {
    mode: parseState(data),
    credentials: {
      ACCESS_TOKEN: parseCredentialString(data.options, "ACCESS_TOKEN"),
    },
  };
}

function decodeDataverse(data: FetchedState): IntegrationStates["DATAVERSE"] {
  return {
    mode: parseState(data),
    credentials:
      Object.entries(data.options).length > 0
        ? Object.entries(data.options).map(([optionsId, config]) =>
            typeof config === "object" &&
            config !== null &&
            typeof config.DATAVERSE_APIKEY === "string" &&
            typeof config.DATAVERSE_URL === "string" &&
            typeof config.DATAVERSE_ALIAS === "string" &&
            typeof config._label === "string"
              ? Optional.present({
                  DATAVERSE_APIKEY: config.DATAVERSE_APIKEY,
                  DATAVERSE_URL: config.DATAVERSE_URL,
                  DATAVERSE_ALIAS: config.DATAVERSE_ALIAS,
                  _label: config._label,
                  optionsId,
                })
              : Optional.empty()
          )
        : [],
  };
}

function decodeDmpTool(data: FetchedState): IntegrationStates["DMPTOOL"] {
  return {
    mode: parseState(data),
    credentials: {
      ACCESS_TOKEN: parseCredentialString(data.options, "ACCESS_TOKEN"),
    },
  };
}

function decodeDmponline(data: FetchedState): IntegrationStates["DMPONLINE"] {
  return { mode: parseState(data), credentials: {} };
}

function decodeDropbox(data: FetchedState): IntegrationStates["DROPBOX"] {
  return { mode: parseState(data), credentials: {} };
}

function decodeDryad(data: FetchedState): IntegrationStates["DRYAD"] {
  return {
    mode: parseState(data),
    credentials: {
      ACCESS_TOKEN: parseCredentialString(data.options, "ACCESS_TOKEN"),
    },
  };
}

function decodeEgnyte(data: FetchedState): IntegrationStates["EGNYTE"] {
  let EGNYTE_DOMAIN = Optional.empty<string>();
  if (Object.values(data.options).length === 1) {
    const option = Object.values(data.options)[0];
    if (typeof option === "object" && option !== null) {
      EGNYTE_DOMAIN = parseCredentialString(option, "EGNYTE_DOMAIN");
    }
  }
  return {
    mode: parseState(data),
    credentials: { EGNYTE_DOMAIN },
  };
}

function decodeEvernote(data: FetchedState): IntegrationStates["EVERNOTE"] {
  return { mode: parseState(data), credentials: {} };
}

function decodeFigshare(data: FetchedState): IntegrationStates["FIGSHARE"] {
  return {
    mode: parseState(data),
    credentials: {
      ACCESS_TOKEN: parseCredentialString(data.options, "ACCESS_TOKEN"),
    },
  };
}

function decodeGitHub(data: FetchedState): IntegrationStates["GITHUB"] {
  return {
    mode: parseState(data),
    credentials:
      Object.entries(data.options).length > 0
        ? Object.entries(data.options).map(([optionsId, config]) => {
            if (typeof config !== "object" || config === null)
              return Optional.empty();
            if (typeof config.GITHUB_REPOSITORY_FULL_NAME !== "string")
              return Optional.empty();
            const name = config.GITHUB_REPOSITORY_FULL_NAME;
            if (typeof config.GITHUB_ACCESS_TOKEN === "string") {
              return Optional.present({
                /*
                 * we use an inner Optional so that the user can see which repo
                 * is in an invalid state, and can thus remove and readd
                 */
                GITHUB_ACCESS_TOKEN: Optional.fromNullable(
                  config.GITHUB_ACCESS_TOKEN
                ),
                GITHUB_REPOSITORY_FULL_NAME: name,
                optionsId,
              });
            }
            return Optional.empty();
          })
        : [],
  };
}

function decodeGoogleDrive(
  data: FetchedState
): IntegrationStates["GOOGLEDRIVE"] {
  return {
    mode: parseState(data),
    credentials: {
      ["googledrive.linking.enabled"]: parseCredentialBoolean(
        data.options,
        "googledrive.linking.enabled"
      ),
    },
  };
}

function decodeJove(data: FetchedState): IntegrationStates["JOVE"] {
  return { mode: parseState(data), credentials: {} };
}

function decodeMendeley(data: FetchedState): IntegrationStates["MENDELEY"] {
  return { mode: parseState(data), credentials: {} };
}

function decodeMsTeams(data: FetchedState): IntegrationStates["MSTEAMS"] {
  return {
    mode: parseState(data),
    credentials: Object.entries(data.options).map(([optionsId, config]) => {
      if (typeof config !== "object" || config === null)
        return Optional.empty();
      if (typeof config.MSTEAMS_CHANNEL_LABEL !== "string")
        return Optional.empty();
      if (typeof config.MSTEAMS_WEBHOOK_URL !== "string")
        return Optional.empty();
      return Optional.present({
        MSTEAMS_CHANNEL_LABEL: config.MSTEAMS_CHANNEL_LABEL,
        MSTEAMS_WEBHOOK_URL: config.MSTEAMS_WEBHOOK_URL,
        optionsId,
      });
    }),
  };
}

function decodeNextCloud(data: FetchedState): IntegrationStates["NEXTCLOUD"] {
  return {
    mode: parseState(data),
    credentials: {
      ACCESS_TOKEN: parseCredentialString(data.options, "ACCESS_TOKEN"),
    },
  };
}

function decodeOmero(data: FetchedState): IntegrationStates["OMERO"] {
  return { mode: parseState(data), credentials: {} };
}

function decodeOneDrive(data: FetchedState): IntegrationStates["ONEDRIVE"] {
  return { mode: parseState(data), credentials: {} };
}

function decodeOwnCloud(data: FetchedState): IntegrationStates["OWNCLOUD"] {
  return {
    mode: parseState(data),
    credentials: {
      ACCESS_TOKEN: parseCredentialString(data.options, "ACCESS_TOKEN"),
    },
  };
}

function decodeProtocolsIo(
  data: FetchedState
): IntegrationStates["PROTOCOLS_IO"] {
  return {
    mode: parseState(data),
    credentials: {
      ACCESS_TOKEN: parseCredentialString(data.options, "ACCESS_TOKEN"),
    },
  };
}

function decodePyrat(data: FetchedState): IntegrationStates["PYRAT"] {
  return {
    mode: parseState(data),
    credentials: {
      PYRAT_USER_TOKEN: parseCredentialString(data.options, "PYRAT_USER_TOKEN"),
    },
  };
}

function decodeSlack(data: FetchedState): IntegrationStates["SLACK"] {
  return {
    mode: parseState(data),
    credentials: Object.entries(data.options).map(([optionsId, config]) => {
      if (typeof config !== "object" || config === null)
        return Optional.empty();
      if (typeof config.SLACK_TEAM_NAME !== "string") return Optional.empty();
      if (typeof config.SLACK_CHANNEL_ID !== "string") return Optional.empty();
      if (typeof config.SLACK_CHANNEL_NAME !== "string")
        return Optional.empty();
      if (typeof config.SLACK_CHANNEL_LABEL !== "string")
        return Optional.empty();
      if (typeof config.SLACK_USER_ID !== "string") return Optional.empty();
      if (typeof config.SLACK_USER_ACCESS_TOKEN !== "string")
        return Optional.empty();
      if (typeof config.SLACK_TEAM_ID !== "string") return Optional.empty();
      if (typeof config.SLACK_WEBHOOK_URL !== "string") return Optional.empty();
      return Optional.present({
        SLACK_TEAM_NAME: config.SLACK_TEAM_NAME,
        SLACK_CHANNEL_ID: config.SLACK_CHANNEL_ID,
        SLACK_CHANNEL_NAME: config.SLACK_CHANNEL_NAME,
        SLACK_USER_ID: config.SLACK_USER_ID,
        SLACK_CHANNEL_LABEL: config.SLACK_CHANNEL_LABEL,
        SLACK_USER_ACCESS_TOKEN: config.SLACK_USER_ACCESS_TOKEN,
        SLACK_TEAM_ID: config.SLACK_TEAM_ID,
        SLACK_WEBHOOK_URL: config.SLACK_WEBHOOK_URL,
        optionsId,
      });
    }),
  };
}

function decodeZenodo(data: FetchedState): IntegrationStates["ZENODO"] {
  return {
    mode: parseState(data),
    credentials: {
      ZENODO_USER_TOKEN: parseCredentialString(
        data.options,
        "ZENODO_USER_TOKEN"
      ),
    },
  };
}

function decodeIntegrationStates(data: {
  [Integration]: FetchedState,
}): IntegrationStates {
  return {
    ARGOS: decodeArgos(data.ARGOS),
    BOX: decodeBox(data.BOX),
    CHEMISTRY: decodeChemistry(data.CHEMISTRY),
    CLUSTERMARKET: decodeClustermarket(data.CLUSTERMARKET),
    DATAVERSE: decodeDataverse(data.DATAVERSE),
    DMPONLINE: decodeDmponline(data.DMPONLINE),
    DMPTOOL: decodeDmpTool(data.DMPTOOL),
    DROPBOX: decodeDropbox(data.DROPBOX),
    DRYAD: decodeDryad(data.DRYAD),
    EGNYTE: decodeEgnyte(data.EGNYTE),
    EVERNOTE: decodeEvernote(data.EVERNOTE),
    FIGSHARE: decodeFigshare(data.FIGSHARE),
    GITHUB: decodeGitHub(data.GITHUB),
    GOOGLEDRIVE: decodeGoogleDrive(data.GOOGLEDRIVE),
    JOVE: decodeJove(data.JOVE),
    MENDELEY: decodeMendeley(data.MENDELEY),
    MSTEAMS: decodeMsTeams(data.MSTEAMS),
    NEXTCLOUD: decodeNextCloud(data.NEXTCLOUD),
    OMERO: decodeOmero(data.OMERO),
    ONEDRIVE: decodeOneDrive(data.ONEDRIVE),
    OWNCLOUD: decodeOwnCloud(data.OWNCLOUD),
    PROTOCOLS_IO: decodeProtocolsIo(data.PROTOCOLS_IO),
    PYRAT: decodePyrat(data.PYRAT),
    SLACK: decodeSlack(data.SLACK),
    ZENODO: decodeZenodo(data.ZENODO),
  };
}

const encodeIntegrationState = <I: Integration>(
  integration: I,
  data: IntegrationStates[I]
): FetchedState => {
  if (integration === "ARGOS") {
    return {
      name: "ARGOS",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {},
    };
  }
  if (integration === "BOX") {
    return {
      name: "BOX",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {
        // $FlowExpectedError[prop-missing]
        // $FlowExpectedError[incompatible-type]
        // $FlowExpectedError[incompatible-use]
        ...data.credentials.BOX_LINK_TYPE.map((token) => ({
          BOX_LINK_TYPE: token,
        })).orElse({
          BOX_LINK_TYPE: "",
        }),
        // $FlowExpectedError[prop-missing]
        // $FlowExpectedError[incompatible-type]
        // $FlowExpectedError[incompatible-use]
        ...data.credentials["box.api.enabled"]
          .map((token) => ({
            ["box.api.enabled"]: token,
          }))
          .orElse({
            ["box.api.enabled"]: "",
          }),
      },
    };
  }
  if (integration === "CHEMISTRY") {
    return {
      name: "CHEMISTRY",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {},
    };
  }
  if (integration === "CLUSTERMARKET") {
    return {
      name: "CLUSTERMARKET",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {},
    };
  }
  if (integration === "DATAVERSE") {
    const creds: IntegrationStates["DATAVERSE"]["credentials"] =
      // $FlowExpectedError[prop-missing]
      // $FlowExpectedError[incompatible-type]
      data.credentials;
    return {
      name: "DATAVERSE",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: Object.fromEntries(
        ArrayUtils.mapOptional<
          Optional<{|
            DATAVERSE_APIKEY: string,
            DATAVERSE_URL: string,
            DATAVERSE_ALIAS: string,
            _label: string,
            optionsId: OptionsId,
          |}>,
          [
            OptionsId,
            {|
              DATAVERSE_APIKEY: string,
              DATAVERSE_URL: string,
              DATAVERSE_ALIAS: string,
              _label: string,
            |}
          ]
        >(
          (config) =>
            config.map((c) => [
              c.optionsId,
              {
                DATAVERSE_ALIAS: c.DATAVERSE_ALIAS,
                DATAVERSE_URL: c.DATAVERSE_URL,
                DATAVERSE_APIKEY: c.DATAVERSE_APIKEY,
                _label: c._label,
              },
            ]),
          creds
        )
      ),
    };
  }
  if (integration === "DMPONLINE") {
    return {
      name: "DMPONLINE",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {},
    };
  }
  if (integration === "DMPTOOL") {
    return {
      name: "DMPTOOL",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      // $FlowExpectedError[prop-missing]
      // $FlowExpectedError[incompatible-type]
      // $FlowExpectedError[incompatible-use]
      options: data.credentials.ACCESS_TOKEN.map((token) => ({
        ACCESS_TOKEN: token,
      })).orElse({}),
    };
  }
  if (integration === "DROPBOX") {
    return {
      name: "DROPBOX",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {},
    };
  }
  if (integration === "DRYAD") {
    return {
      name: "DRYAD",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {
        // $FlowExpectedError[prop-missing]
        // $FlowExpectedError[incompatible-type]
        // $FlowExpectedError[incompatible-use]
        ...data.credentials.ACCESS_TOKEN.map((token) => ({
          ACCESS_TOKEN: token,
        })).orElse({}),
      },
    };
  }
  if (integration === "EGNYTE") {
    return {
      name: "EGNYTE",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {
        // $FlowExpectedError[prop-missing]
        // $FlowExpectedError[incompatible-type]
        // $FlowExpectedError[incompatible-use]
        ...data.credentials.EGNYTE_DOMAIN.map((token) => ({
          EGNYTE_DOMAIN: token,
        })).orElse({}),
      },
    };
  }
  if (integration === "EVERNOTE") {
    return {
      name: "EVERNOTE",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {},
    };
  }
  if (integration === "FIGSHARE") {
    return {
      name: "FIGSHARE",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {
        // $FlowExpectedError[prop-missing]
        // $FlowExpectedError[incompatible-type]
        // $FlowExpectedError[incompatible-use]
        ...data.credentials.ACCESS_TOKEN.map((token) => ({
          ACCESS_TOKEN: token,
        })).orElse({}),
      },
    };
  }
  if (integration === "GITHUB") {
    return {
      name: "GITHUB",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: Object.fromEntries(
        ArrayUtils.mapOptional<
          Optional<{|
            GITHUB_ACCESS_TOKEN: string,
            GITHUB_REPOSITORY_FULL_NAME: string,
            optionsId: OptionsId,
          |}>,
          [
            OptionsId,
            {|
              GITHUB_ACCESS_TOKEN: string,
              GITHUB_REPOSITORY_FULL_NAME: string,
            |}
          ]
        >(
          (config) =>
            config.map((c) => [
              c.optionsId,
              {
                GITHUB_ACCESS_TOKEN: c.GITHUB_ACCESS_TOKEN,
                GITHUB_REPOSITORY_FULL_NAME: c.GITHUB_REPOSITORY_FULL_NAME,
              },
            ]),
          // $FlowExpectedError[prop-missing]
          // $FlowExpectedError[incompatible-call]
          data.credentials
        )
      ),
    };
  }
  if (integration === "GOOGLEDRIVE") {
    return {
      name: "GOOGLEDRIVE",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {
        // $FlowExpectedError[prop-missing]
        // $FlowExpectedError[incompatible-use]
        ...data.credentials["googledrive.linking.enabled"]
          .map((enabled) => ({
            ["googledrive.linking.enabled"]: enabled,
          }))
          .orElse({}),
      },
    };
  }
  if (integration === "JOVE") {
    return {
      name: "JOVE",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {},
    };
  }
  if (integration === "MENDELEY") {
    return {
      name: "MENDELEY",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {},
    };
  }
  if (integration === "MSTEAMS") {
    return {
      name: "MSTEAMS",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {},
    };
  }
  if (integration === "NEXTCLOUD") {
    return {
      name: "NEXTCLOUD",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {
        // $FlowExpectedError[prop-missing]
        // $FlowExpectedError[incompatible-type]
        // $FlowExpectedError[incompatible-use]
        ...data.credentials.ACCESS_TOKEN.map((token) => ({
          ACCESS_TOKEN: token,
        })).orElse({}),
      },
    };
  }
  if (integration === "OMERO") {
    return {
      name: "OMERO",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {},
    };
  }
  if (integration === "ONEDRIVE") {
    return {
      name: "ONEDRIVE",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {},
    };
  }
  if (integration === "OWNCLOUD") {
    return {
      name: "OWNCLOUD",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {
        // $FlowExpectedError[prop-missing]
        // $FlowExpectedError[incompatible-type]
        // $FlowExpectedError[incompatible-use]
        ...data.credentials.ACCESS_TOKEN.map((token) => ({
          ACCESS_TOKEN: token,
        })).orElse({}),
      },
    };
  }
  if (integration === "PROTOCOLS_IO") {
    return {
      name: "PROTOCOLS_IO",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {},
    };
  }
  if (integration === "PYRAT") {
    return {
      name: "PYRAT",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {
        // $FlowExpectedError[prop-missing]
        // $FlowExpectedError[incompatible-type]
        // $FlowExpectedError[incompatible-use]
        ...data.credentials.PYRAT_USER_TOKEN.map((token) => ({
          PYRAT_USER_TOKEN: token,
        })).orElse({
          PYRAT_USER_TOKEN: "",
        }),
      },
    };
  }
  if (integration === "SLACK") {
    return {
      name: "SLACK",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      /*
       * SLACK options are not encoded as the backend ignores them when /update
       * is called. They are configured by calls to /saveAppOptions and
       * /deleteAppOptions instead
       */
      options: {},
    };
  }
  if (integration === "ZENODO") {
    return {
      name: "ZENODO",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {
        // $FlowExpectedError[prop-missing]
        // $FlowExpectedError[incompatible-type]
        // $FlowExpectedError[incompatible-use]
        ...data.credentials.ZENODO_USER_TOKEN.map((token) => ({
          ZENODO_USER_TOKEN: token,
        })).orElse({
          ZENODO_USER_TOKEN: "",
        }),
      },
    };
  }
  throw new Error(
    `encodeIntegrationState has not been implemented for integration ${integration}`
  );
};

const ONE_MINUTE_IN_MS = 60 * 60 * 1000;

/**
 * This is a custom hook that allows for react components to fetch the current
 * state of all of the integrations from the server and update the state when
 * the user makes a change.
 *
 * @context Alert This custom hook relies on there being an instance of the Alert
 *                context to display toasts after the calls to modify the state
 *                of the integrations has either failed or completed successfully.
 */
export function useIntegrationsEndpoint(): {|
  /**
   * Calls the `/allIntegrations` API endpoint.
   *
   * @returns A promise of the current state of all of the integrations.
   */
  allIntegrations: () => Promise<IntegrationStates>,

  /**
   * Calls the `/update` API endpoint, updating the mode of the integration
   * (whether it is enabled or disabled, as unavailable is configured by
   * sysadmin) and any simple configuration options like API keys.
   *
   * @return Whilst this function displays an error toast if the action was not
   *         successful, the returned promise will still reject with an
   *         `Error`. Should the update be successful, then a success toast is
   *         shown and the returned promise resolves with void.
   */
  update: <I: Integration & string>(
    integration: I,
    newState: IntegrationStates[I]
  ) => Promise<IntegrationStates[I]>,

  /**
   * Calls the `/saveAppOptions` API endpoint, creating a new configuration or
   * overwriting an existing one, for integrations that support an array of
   * configurations.
   *
   * @param appName   The name of the integration.
   * @param optionsId MUST be Optional.empty if a new configuration is being
   *                  created, else Optional.present wrapping the existing
   *                  configuration's optionsId when overwriting.
   * @param newOption Whilst typed as any object, the exact required object
   *                  will depend on the options of the specific integration.
   *                  Typically the same as the integration's credentials
   *                  object, as specified at the top of this file, but may
   *                  vary slightly.
   *
   * @return If the network call fails then the returned Promise will be in a
   *         rejected state, wrapping an instance of `Error`. The caller SHOULD
   *         display an alert to indicate to the user that the action failed,
   *         displaying `error.message`. If it succeeds then an updated state
   *         for the integration will be returned, wrapped in Promise.resolve.
   *         Called SHOULD update any UI state, using runInAction, so that the
   *         UI updates.
   */
  saveAppOptions: <I: Integration & string>(
    appName: I,
    optionsId: Optional<OptionsId>,
    newOption: { ... }
  ) => Promise<IntegrationStates[I]>,

  /**
   * Calls the `deleteAppOptions` API endpoint, deleting a configuration option
   * for the integrations that support an array of configurations.
   *
   * @return If the network call fails then the returned Promise will be in a
   *         rejected state, wrapping an instance of Error. It is recommended
   *         that the caller displays an alert to indicate to the user that the
   *         action failed, displaying `error.message`.
   */
  deleteAppOptions: <I: Integration & string>(
    appName: I,
    optionsId: OptionsId
  ) => Promise<void>,
|} {
  const api = axios.create({
    baseURL: "/integration",
    timeout: ONE_MINUTE_IN_MS,
  });
  const { addAlert } = useContext(AlertContext);

  const allIntegrations = async (): Promise<IntegrationStates> => {
    const states = await api.get<
      | {|
          success: true,
          data: { [Integration]: FetchedState },
          error: null,
        |}
      | {|
          success: false,
          data: null,
          error: string,
        |}
    >("allIntegrations");
    if (states.data.success) {
      const data = states.data.data;
      return decodeIntegrationStates(data);
    }
    throw new Error(states.data.error);
  };

  const update = React.useCallback(
    async <I: Integration & string>(
      integration: I,
      newState: IntegrationStates[I]
    ): Promise<IntegrationStates[I]> => {
      try {
        const { data: responseData } = await api.post<
          FetchedState,
          | {| success: true, data: FetchedState |}
          | {|
              success: false,
              data: null,
              errorMsg:
                | string
                | {|
                    errorMessages: Array<string>,
                  |},
            |}
          | {| errorId: string, exceptionMessage: string, tstamp: string |}
        >("update", encodeIntegrationState(integration, newState));

        if (!responseData.success) {
          if (
            responseData.errorMsg !== null &&
            typeof responseData.errorMsg !== "undefined"
          ) {
            const errorMsg = responseData.errorMsg;
            if (typeof errorMsg === "string") {
              throw new Error(errorMsg);
            } else {
              throw new Error(
                ArrayUtils.getAt(0, errorMsg.errorMessages).orElse(
                  "Unknown reason"
                )
              );
            }
          }
          throw new Error(responseData.exceptionMessage);
        } else {
          addAlert(
            mkAlert({
              variant: "success",
              message: "Update successful.",
            })
          );
          switch (integration) {
            case "ARGOS":
              return decodeArgos(responseData.data);
            case "BOX":
              return decodeBox(responseData.data);
            case "CHEMISTRY":
              return decodeChemistry(responseData.data);
            case "CLUSTERMARKET":
              return decodeClustermarket(responseData.data);
            case "DATAVERSE":
              return decodeDataverse(responseData.data);
            case "DMPONLINE":
              return decodeDmponline(responseData.data);
            case "DMPTOOL":
              return decodeDmpTool(responseData.data);
            case "DROPBOX":
              return decodeDropbox(responseData.data);
            case "DRYAD":
              return decodeDryad(responseData.data);
            case "EGNYTE":
              return decodeEgnyte(responseData.data);
            case "EVERNOTE":
              return decodeEvernote(responseData.data);
            case "FIGSHARE":
              return decodeFigshare(responseData.data);
            case "GITHUB":
              return decodeGitHub(responseData.data);
            case "GOOGLEDRIVE":
              return decodeGoogleDrive(responseData.data);
            case "JOVE":
              return decodeJove(responseData.data);
            case "MENDELEY":
              return decodeMendeley(responseData.data);
            case "MSTEAMS":
              return decodeMsTeams(responseData.data);
            case "NEXTCLOUD":
              return decodeNextCloud(responseData.data);
            case "OMERO":
              return decodeOmero(responseData.data);
            case "ONEDRIVE":
              return decodeOneDrive(responseData.data);
            case "OWNCLOUD":
              return decodeOwnCloud(responseData.data);
            case "PROTOCOLS_IO":
              return decodeProtocolsIo(responseData.data);
            case "PYRAT":
              return decodePyrat(responseData.data);
            case "SLACK":
              return decodeSlack(responseData.data);
            case "ZENODO":
              return decodeZenodo(responseData.data);
            default:
              throw new Error("Invalid integration");
          }
        }
      } catch (e) {
        addAlert(
          mkAlert({
            variant: "error",
            title: "Update failed.",
            message: e.message,
          })
        );
        throw e;
      }
    },
    []
  );

  const saveAppOptions = async <I: Integration & string>(
    appName: I,
    optionsId: Optional<OptionsId>,
    newOption: { ... }
  ): Promise<IntegrationStates[I]> => {
    const params = new URLSearchParams({
      appName,
      ...optionsId.map((o) => ({ optionsId: o })).orElse({}),
    });
    const response = await api.post<
      { ... },
      | {| success: true, data: FetchedState |}
      | {| success: false, data: null, errorMsg: string |}
      | {| errorId: string, exceptionMessage: string, tstamp: string |}
    >("saveAppOptions", newOption, { params });

    if (!response.data.success) {
      if (
        response.data.errorMsg !== null &&
        typeof response.data.errorMsg !== "undefined"
      ) {
        throw new Error(response.data.errorMsg);
      }
      if (
        response.data.exceptionMessage !== null &&
        typeof response.data.exceptionMessage !== "undefined"
      ) {
        throw new Error(response.data.exceptionMessage);
      }
      throw new Error("Unknown reason");
    } else {
      switch (appName) {
        case "ARGOS":
          return decodeArgos(response.data.data);
        case "BOX":
          return decodeBox(response.data.data);
        case "CHEMISTRY":
          return decodeChemistry(response.data.data);
        case "CLUSTERMARKET":
          return decodeClustermarket(response.data.data);
        case "DATAVERSE":
          return decodeDataverse(response.data.data);
        case "DMPONLINE":
          return decodeDmponline(response.data.data);
        case "DMPTOOL":
          return decodeDmpTool(response.data.data);
        case "DROPBOX":
          return decodeDropbox(response.data.data);
        case "DRYAD":
          return decodeDryad(response.data.data);
        case "EGNYTE":
          return decodeEgnyte(response.data.data);
        case "EVERNOTE":
          return decodeEvernote(response.data.data);
        case "FIGSHARE":
          return decodeFigshare(response.data.data);
        case "GITHUB":
          return decodeGitHub(response.data.data);
        case "GOOGLEDRIVE":
          return decodeGoogleDrive(response.data.data);
        case "JOVE":
          return decodeJove(response.data.data);
        case "MENDELEY":
          return decodeMendeley(response.data.data);
        case "MSTEAMS":
          return decodeMsTeams(response.data.data);
        case "NEXTCLOUD":
          return decodeNextCloud(response.data.data);
        case "OMERO":
          return decodeOmero(response.data.data);
        case "ONEDRIVE":
          return decodeOneDrive(response.data.data);
        case "OWNCLOUD":
          return decodeOwnCloud(response.data.data);
        case "PROTOCOLS_IO":
          return decodeProtocolsIo(response.data.data);
        case "PYRAT":
          return decodePyrat(response.data.data);
        case "SLACK":
          return decodeSlack(response.data.data);
        case "ZENODO":
          return decodeZenodo(response.data.data);
        default:
          throw new Error("Invalid integration");
      }
    }
  };

  const deleteAppOptions = async <I: Integration & string>(
    appName: I,
    optionsId: OptionsId
  ): Promise<void> => {
    const formData = new FormData();
    formData.append("optionsId", `${optionsId}`);
    const response = await api.post<
      FormData,
      | {| success: true, data: FetchedState |}
      | {| success: false, data: null, errorMsg: string |}
      | {| errorId: string, exceptionMessage: string, tstamp: string |}
    >("deleteAppOptions", formData, {
      params: new URLSearchParams({ appName }),
    });

    // if success, update states so that the UI updates
    if (!response.data.success) {
      if (
        response.data.exceptionMessage !== null &&
        typeof response.data.exceptionMessage !== "undefined"
      ) {
        throw new Error(response.data.errorMsg);
      }
      if (response.data.exceptionMessage) {
        throw new Error(response.data.exceptionMessage);
      }
    }
  };

  return { allIntegrations, update, saveAppOptions, deleteAppOptions };
}
