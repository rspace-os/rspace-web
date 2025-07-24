//@flow strict

import React, { useContext } from "react";
import axios from "@/common/axios";
import { getByKey, Optional } from "../../util/optional";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import * as ArrayUtils from "../../util/ArrayUtils";
import { parseString } from "../../util/parsers";
import Result from "../../util/result";
import * as Parsers from "../../util/parsers";
import { type emptyObject } from "../../util/types";

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
export type IntegrationState<Credentials> = {
  /**
   * UNAVAILABLE : When the sysadmin has not allowed the integration.
   * DISABLED    : Sysadmin has allowed it but the user has not enabled it.
   * ENABLED     : Sysadmin has allowed it and the user has enabled it.
   * EXTERNAL    : The app is a third-party integrating with RSpace.
   */
  mode: "UNAVAILABLE" | "DISABLED" | "ENABLED" | "EXTERNAL";
  credentials: Credentials;
};

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
export type IntegrationStates = {
  API_DIRECT: IntegrationState<null>;
  ARGOS: IntegrationState<emptyObject>;
  ASCENSCIA: IntegrationState<emptyObject>;
  BOX: IntegrationState<{
    BOX_LINK_TYPE: Optional<"LIVE" | "VERSIONED" | "ASK">;
    "box.api.enabled": Optional<boolean>;
  }>;
  CHEMISTRY: IntegrationState<emptyObject>;
  CLUSTERMARKET: IntegrationState<{
    ACCESS_TOKEN: Optional<string>;
  }>;
  DATAVERSE: IntegrationState<
    Array<
      Optional<{
        DATAVERSE_APIKEY: string;
        DATAVERSE_URL: string;
        DATAVERSE_ALIAS: string;
        _label: string;
        optionsId: OptionsId;
      }>
    >
  >;
  DIGITALCOMMONSDATA: IntegrationState<{
    ACCESS_TOKEN: Optional<string>;
  }>;
  DMPONLINE: IntegrationState<{
    ACCESS_TOKEN: Optional<string>;
  }>;
  DMPTOOL: IntegrationState<{
    ACCESS_TOKEN: Optional<string>;
  }>;
  DROPBOX: IntegrationState<emptyObject>;
  DRYAD: IntegrationState<{
    ACCESS_TOKEN: Optional<string>;
  }>;
  EGNYTE: IntegrationState<{
    EGNYTE_DOMAIN: Optional<string>;
  }>;
  EVERNOTE: IntegrationState<emptyObject>;
  FIELDMARK: IntegrationState<{
    FIELDMARK_USER_TOKEN: Optional<string>;
  }>;
  FIGSHARE: IntegrationState<{
    ACCESS_TOKEN: Optional<string>;
  }>;
  GALAXY: IntegrationState<{
    GALAXY_API_KEY: Optional<string>;
    }>;
  GITHUB: IntegrationState<
    Array<
      Optional<{
        /*
         * we use an inner Optional so that the user can see which repo is in an
         * invalid state, and can thus remove and readd
         */
        GITHUB_ACCESS_TOKEN: Optional<string>;
        GITHUB_REPOSITORY_FULL_NAME: string;
        optionsId: OptionsId;
      }>
    >
  >;
  GOOGLEDRIVE: IntegrationState<{
    ["googledrive.linking.enabled"]: Optional<boolean>;
  }>;
  JOVE: IntegrationState<emptyObject>;
  MSTEAMS: IntegrationState<
    Array<
      Optional<{
        MSTEAMS_CHANNEL_LABEL: string;
        MSTEAMS_WEBHOOK_URL: string;
        optionsId: OptionsId;
      }>
    >
  >;
  NEXTCLOUD: IntegrationState<{
    ACCESS_TOKEN: Optional<string>;
  }>;
  OMERO: IntegrationState<emptyObject>;
  ONEDRIVE: IntegrationState<emptyObject>;
  OWNCLOUD: IntegrationState<{
    ACCESS_TOKEN: Optional<string>;
  }>;
  PROTOCOLS_IO: IntegrationState<{
    ACCESS_TOKEN: Optional<string>;
  }>;
  PYRAT: IntegrationState<{
    configuredServers: ReadonlyArray<{
      url: string;
      alias: string;
    }>;
    authenticatedServers: ReadonlyArray<{
      url: string;
      alias: string;
      apiKey: string;
      optionsId: OptionsId;
    }>;
  }>;
  SLACK: IntegrationState<
    Array<
      Optional<{
        SLACK_TEAM_NAME: string;
        SLACK_CHANNEL_ID: string;
        SLACK_CHANNEL_NAME: string;
        SLACK_USER_ID: string;
        SLACK_CHANNEL_LABEL: string;
        SLACK_USER_ACCESS_TOKEN: string;
        SLACK_TEAM_ID: string;
        SLACK_WEBHOOK_URL: string;
        optionsId: OptionsId;
      }>
    >
  >;
  ZENODO: IntegrationState<{
    ZENODO_USER_TOKEN: Optional<string>;
  }>;
};

type FetchedState = {
  name: string;
  available: boolean;
  enabled: boolean;
  options: Record<string, unknown>;
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
}: FetchedState): IntegrationState<unknown>["mode"] {
  if (!available) return "UNAVAILABLE";
  if (!enabled) return "DISABLED";
  return "ENABLED";
}

/**
 * Checks that `options` has a key-value pair with the key `key`, and if it
 * does that that value is a string. If so, then the value is returned wrapped
 * in Optional.present, otherwise Optional.empty is returned.
 */
function parseCredentialString<K extends string>(
  options: Record<K, unknown>,
  key: K
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
function parseCredentialBoolean<K extends string>(
  options: Record<K, unknown>,
  key: K
): Optional<boolean> {
  return getByKey(key, options).flatMap((cred) =>
    typeof cred === "boolean" ? Optional.present(cred) : Optional.empty()
  );
}

export type Integration = keyof IntegrationStates;

function decodeArgos(data: FetchedState): IntegrationStates["ARGOS"] {
  return { mode: parseState(data), credentials: {} };
}

function decodeAscenscia(data: FetchedState): IntegrationStates["ASCENSCIA"] {
  return {
    mode: parseState(data),
    credentials: {}
  };
}
function decodeBox(data: FetchedState): IntegrationStates["BOX"] {
  return {
    mode: parseState(data),
    credentials: {
      BOX_LINK_TYPE: Parsers.getValueWithKey("BOX_LINK_TYPE")(data.options)
        .flatMap(Parsers.isString)
        .flatMap((option) =>
          Result.first(
            parseString("LIVE", option),
            parseString("VERSIONED", option),
            parseString("ASK", option)
          )
        )
        .toOptional(),
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
  function isValidConfig(config: unknown): config is {
    DATAVERSE_APIKEY: string;
    DATAVERSE_URL: string;
    DATAVERSE_ALIAS: string;
    _label: string;
  } {
    return Parsers.isObject(config)
      .flatMap(Parsers.isNotNull)
      .flatMap(Parsers.isRecord)
      .map<boolean>((configRecord: Record<string, unknown>) => {
        return (
          typeof configRecord.DATAVERSE_APIKEY === "string" &&
          typeof configRecord.DATAVERSE_URL === "string" &&
          typeof configRecord.DATAVERSE_ALIAS === "string" &&
          typeof configRecord._label === "string"
        );
      })
      .orElse(false);
  }
  return {
    mode: parseState(data),
    credentials:
      Object.entries(data.options).length > 0
        ? Object.entries(data.options).map(([optionsId, config]) =>
            isValidConfig(config)
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

function decodeDigitalCommonsData(
  data: FetchedState
): IntegrationStates["DIGITALCOMMONSDATA"] {
  return {
    mode: parseState(data),
    credentials: {
      ACCESS_TOKEN: parseCredentialString(
        data.options,
        "DIGITAL_COMMONS_DATA_USER_TOKEN"
      ),
    },
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
  return {
    mode: parseState(data),
    credentials: {
      ACCESS_TOKEN: parseCredentialString(data.options, "DMPONLINE_USER_TOKEN"),
    },
  };
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
  return {
    mode: parseState(data),
    credentials: {
      EGNYTE_DOMAIN:
        Object.values(data.options).length === 1
          ? ArrayUtils.head(Object.values(data.options))
              .flatMap(Parsers.isObject)
              .flatMap(Parsers.isNotNull)
              .flatMap(Parsers.isRecord)
              .toOptional()
              .flatMap((option) =>
                parseCredentialString(option, "EGNYTE_DOMAIN")
              )
          : Optional.empty(),
    },
  };
}

function decodeEvernote(data: FetchedState): IntegrationStates["EVERNOTE"] {
  return { mode: parseState(data), credentials: {} };
}

function decodeFieldmark(data: FetchedState): IntegrationStates["FIELDMARK"] {
  return {
    mode: parseState(data),
    credentials: {
      FIELDMARK_USER_TOKEN: parseCredentialString(
        data.options,
        "FIELDMARK_USER_TOKEN"
      ),
    },
  };
}

function decodeFigshare(data: FetchedState): IntegrationStates["FIGSHARE"] {
  return {
    mode: parseState(data),
    credentials: {
      ACCESS_TOKEN: parseCredentialString(data.options, "ACCESS_TOKEN"),
    },
  };
}

function decodeGalaxy(data: FetchedState): IntegrationStates["GALAXY"] {
  return {
    mode: parseState(data),
    credentials: {
      GALAXY_API_KEY: parseCredentialString(data.options, "GALAXY_API_KEY"),
    },
  };
}

function decodeGitHub(data: FetchedState): IntegrationStates["GITHUB"] {
  return {
    mode: parseState(data),
    credentials:
      Object.entries(data.options).length > 0
        ? Object.entries(data.options).map(([optionsId, config]) =>
            Parsers.isObject(config)
              .flatMap(Parsers.isNotNull)
              .flatMap(Parsers.isRecord)
              .flatMap((configRecord) =>
                Parsers.isString(configRecord.GITHUB_REPOSITORY_FULL_NAME).map(
                  (GITHUB_REPOSITORY_FULL_NAME) => ({
                    /*
                     * we use an inner Optional so that the user can see which repo
                     * is in an invalid state, and can thus remove and readd
                     */
                    GITHUB_ACCESS_TOKEN: Parsers.isString(
                      configRecord.GITHUB_ACCESS_TOKEN
                    ).toOptional(),
                    GITHUB_REPOSITORY_FULL_NAME,
                    optionsId,
                  })
                )
              )
              .toOptional()
          )
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

function decodeMsTeams(data: FetchedState): IntegrationStates["MSTEAMS"] {
  return {
    mode: parseState(data),
    credentials: Object.entries(data.options).map(([optionsId, config]) => {
      return Parsers.isObject(config)
        .flatMap(Parsers.isNotNull)
        .flatMap(Parsers.isRecord)
        .flatMap((configRecord) =>
          Result.lift2<
            string,
            string,
            {
              MSTEAMS_CHANNEL_LABEL: string;
              MSTEAMS_WEBHOOK_URL: string;
              optionsId: string;
            }
          >((MSTEAMS_CHANNEL_LABEL, MSTEAMS_WEBHOOK_URL) => ({
            MSTEAMS_CHANNEL_LABEL,
            MSTEAMS_WEBHOOK_URL,
            optionsId,
          }))(
            Parsers.isString(configRecord.MSTEAMS_CHANNEL_LABEL),
            Parsers.isString(configRecord.MSTEAMS_WEBHOOK_URL)
          )
        )
        .toOptional();
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
      configuredServers: Parsers.objectPath(
        ["options", "PYRAT_CONFIGURED_SERVERS"],
        data
      )
        .flatMap(Parsers.isObject)
        .flatMap(Parsers.isNotNull)
        .flatMap((configuredServers) =>
          Result.all(
            ...Object.values(configuredServers).map((config: unknown) => {
              try {
                const server = Parsers.isObject(config)
                  .flatMap(Parsers.isNotNull)
                  .elseThrow();
                const alias = Parsers.getValueWithKey("alias")(server)
                  .flatMap(Parsers.isString)
                  .elseThrow();
                const url = Parsers.getValueWithKey("url")(server)
                  .flatMap(Parsers.isString)
                  .elseThrow();
                return Result.Ok({ alias, url });
              } catch {
                return Result.Error<{
                  url: string;
                  alias: string;
                }>([new Error("Could not parse out pyrat configured server")]);
              }
            })
          )
        )
        .orElse([]),
      authenticatedServers: Parsers.objectPath(["options"], data)
        .flatMap(Parsers.isObject)
        .flatMap(Parsers.isNotNull)
        .map((servers) =>
          Object.entries(servers).filter(
            ([k]) => k !== "PYRAT_CONFIGURED_SERVERS"
          )
        )
        .flatMap((servers) =>
          Result.all(
            ...servers.map(([key, config]: [string, unknown]) => {
              try {
                const server = Parsers.isObject(config)
                  .flatMap(Parsers.isNotNull)
                  .elseThrow();
                const alias = Parsers.getValueWithKey("PYRAT_ALIAS")(server)
                  .flatMap(Parsers.isString)
                  .elseThrow();
                const url = Parsers.getValueWithKey("PYRAT_URL")(server)
                  .flatMap(Parsers.isString)
                  .elseThrow();
                const apiKey = Parsers.getValueWithKey("PYRAT_APIKEY")(server)
                  .flatMap(Parsers.isString)
                  .elseThrow();
                const optionsId = Parsers.isString(key).elseThrow();
                return Result.Ok({ alias, url, apiKey, optionsId });
              } catch {
                return Result.Error<{
                  url: string;
                  alias: string;
                  apiKey: string;
                  optionsId: OptionsId;
                }>([
                  new Error("Could not parse out pyrat authenticated server"),
                ]);
              }
            })
          )
        )
        .orElse([]),
    },
  };
}

function decodeSlack(data: FetchedState): IntegrationStates["SLACK"] {
  return {
    mode: parseState(data),
    credentials: Object.entries(data.options).map(([optionsId, config]) => {
      return Parsers.isObject(config)
        .flatMap(Parsers.isNotNull)
        .flatMap(Parsers.isRecord)
        .toOptional()
        .flatMap((configRecord) => {
          try {
            const SLACK_TEAM_NAME = Parsers.isString(
              configRecord.SLACK_TEAM_NAME
            ).elseThrow();
            const SLACK_CHANNEL_ID = Parsers.isString(
              configRecord.SLACK_CHANNEL_ID
            ).elseThrow();
            const SLACK_CHANNEL_NAME = Parsers.isString(
              configRecord.SLACK_CHANNEL_NAME
            ).elseThrow();
            const SLACK_CHANNEL_LABEL = Parsers.isString(
              configRecord.SLACK_CHANNEL_LABEL
            ).elseThrow();
            const SLACK_USER_ID = Parsers.isString(
              configRecord.SLACK_USER_ID
            ).elseThrow();
            const SLACK_USER_ACCESS_TOKEN = Parsers.isString(
              configRecord.SLACK_USER_ACCESS_TOKEN
            ).elseThrow();
            const SLACK_TEAM_ID = Parsers.isString(
              configRecord.SLACK_TEAM_ID
            ).elseThrow();
            const SLACK_WEBHOOK_URL = Parsers.isString(
              configRecord.SLACK_WEBHOOK_URL
            ).elseThrow();
            return Optional.present({
              SLACK_TEAM_NAME,
              SLACK_CHANNEL_ID,
              SLACK_CHANNEL_NAME,
              SLACK_CHANNEL_LABEL,
              SLACK_USER_ID,
              SLACK_USER_ACCESS_TOKEN,
              SLACK_TEAM_ID,
              SLACK_WEBHOOK_URL,
              optionsId,
            });
          } catch {
            return Optional.empty();
          }
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
  [integration in Integration]: FetchedState;
}): IntegrationStates {
  return {
    API_DIRECT: {
      mode: "EXTERNAL",
      credentials: null,
    },
    ARGOS: decodeArgos(data.ARGOS),
    ASCENSCIA: decodeAscenscia(data.ASCENSCIA),
    BOX: decodeBox(data.BOX),
    CHEMISTRY: decodeChemistry(data.CHEMISTRY),
    CLUSTERMARKET: decodeClustermarket(data.CLUSTERMARKET),
    DATAVERSE: decodeDataverse(data.DATAVERSE),
    DIGITALCOMMONSDATA: decodeDigitalCommonsData(data.DIGITALCOMMONSDATA),
    DMPONLINE: decodeDmponline(data.DMPONLINE),
    DMPTOOL: decodeDmpTool(data.DMPTOOL),
    DROPBOX: decodeDropbox(data.DROPBOX),
    DRYAD: decodeDryad(data.DRYAD),
    EGNYTE: decodeEgnyte(data.EGNYTE),
    EVERNOTE: decodeEvernote(data.EVERNOTE),
    FIELDMARK: decodeFieldmark(data.FIELDMARK),
    FIGSHARE: decodeFigshare(data.FIGSHARE),
    GALAXY: decodeGalaxy(data.GALAXY),
    GITHUB: decodeGitHub(data.GITHUB),
    GOOGLEDRIVE: decodeGoogleDrive(data.GOOGLEDRIVE),
    JOVE: decodeJove(data.JOVE),
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

// eslint-disable-next-line complexity
const encodeIntegrationState = <I extends Integration>(
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
  if (integration === "ASCENSCIA") {
    // @ts-expect-error Looks like this is a bug in TypeScript?
    const creds: IntegrationStates["ASCENSCIA"]["credentials"] =
      data.credentials;
    return {
      name: "ASCENSCIA",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {},
    };
  }
  if (integration === "BOX") {
    // @ts-expect-error Looks like this is a bug in TypeScript?
    const creds: IntegrationStates["BOX"]["credentials"] = data.credentials;
    return {
      name: "BOX",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {
        ...creds.BOX_LINK_TYPE.map((token) => ({
          BOX_LINK_TYPE: token,
        })).orElse({
          BOX_LINK_TYPE: "",
        }),
        ...creds["box.api.enabled"]
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
    // @ts-expect-error Looks like this is a bug in TypeScript
    const creds: IntegrationStates["DATAVERSE"]["credentials"] =
      data.credentials;
    return {
      name: "DATAVERSE",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: Object.fromEntries(
        ArrayUtils.mapOptional<
          Optional<{
            DATAVERSE_APIKEY: string;
            DATAVERSE_URL: string;
            DATAVERSE_ALIAS: string;
            _label: string;
            optionsId: OptionsId;
          }>,
          [
            OptionsId,
            {
              DATAVERSE_APIKEY: string;
              DATAVERSE_URL: string;
              DATAVERSE_ALIAS: string;
              _label: string;
            }
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
  if (integration === "DIGITALCOMMONSDATA") {
    // @ts-expect-error Looks like this is a bug in TypeScript
    const creds: IntegrationStates["DIGITALCOMMONSDATA"]["credentials"] =
      data.credentials;
    return {
      name: "DIGITALCOMMONSDATA",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: creds.ACCESS_TOKEN.map((token) => ({
        ACCESS_TOKEN: token,
      })).orElse({}),
    };
  }
  if (integration === "DMPONLINE") {
    // @ts-expect-error Looks like this is a bug in TypeScript?
    const creds: IntegrationStates["DMPONLINE"]["credentials"] =
      data.credentials;
    return {
      name: "DMPONLINE",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: creds.ACCESS_TOKEN.map((token) => ({
        ACCESS_TOKEN: token,
      })).orElse({}),
    };
  }
  if (integration === "DMPTOOL") {
    // @ts-expect-error Looks like this is a bug in TypeScript?
    const creds: IntegrationStates["DMPTOOL"]["credentials"] = data.credentials;
    return {
      name: "DMPTOOL",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: creds.ACCESS_TOKEN.map((token) => ({
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
    // @ts-expect-error Looks like this is a bug in TypeScript?
    const creds: IntegrationStates["DRYAD"]["credentials"] = data.credentials;
    return {
      name: "DRYAD",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {
        ...creds.ACCESS_TOKEN.map((token) => ({
          ACCESS_TOKEN: token,
        })).orElse({}),
      },
    };
  }
  if (integration === "EGNYTE") {
    // @ts-expect-error Looks like this is a bug in TypeScript?
    const creds: IntegrationStates["EGNYTE"]["credentials"] = data.credentials;
    return {
      name: "EGNYTE",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {
        ...creds.EGNYTE_DOMAIN.map((token) => ({
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
  if (integration === "FIELDMARK") {
    // @ts-expect-error Looks like this is a bug in TypeScript?
    const creds: IntegrationStates["FIELDMARK"]["credentials"] =
      data.credentials;
    return {
      name: "FIELDMARK",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: creds.FIELDMARK_USER_TOKEN.map((token) => ({
        FIELDMARK_USER_TOKEN: token,
      })).orElse({
        FIELDMARK_USER_TOKEN: "",
      }),
    };
  }
  if (integration === "FIGSHARE") {
    // @ts-expect-error Looks like this is a bug in TypeScript?
    const creds: IntegrationStates["FIGSHARE"]["credentials"] =
      data.credentials;
    return {
      name: "FIGSHARE",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {
        ...creds.ACCESS_TOKEN.map((token) => ({
          ACCESS_TOKEN: token,
        })).orElse({}),
      },
    };
  }
  if (integration === "GALAXY") {
    // @ts-expect-error Looks like this is a bug in TypeScript?
    const creds: IntegrationStates["GALAXY"]["credentials"] = data.credentials;
    return {
      name: "GALAXY",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {
        ...creds.GALAXY_API_KEY.map((key) => ({
          GALAXY_API_KEY: key,
        })).orElse({
          GALAXY_API_KEY: "",
        }),
      },
    };
  }
  if (integration === "GITHUB") {
    // @ts-expect-error Looks like this is a bug in TypeScript?
    const creds: IntegrationStates["GITHUB"]["credentials"] = data.credentials;
    return {
      name: "GITHUB",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: Object.fromEntries(
        ArrayUtils.mapOptional(
          (config) =>
            config.map((c) => [
              c.optionsId,
              {
                GITHUB_ACCESS_TOKEN: c.GITHUB_ACCESS_TOKEN,
                GITHUB_REPOSITORY_FULL_NAME: c.GITHUB_REPOSITORY_FULL_NAME,
              },
            ]),
          creds
        )
      ),
    };
  }
  if (integration === "GOOGLEDRIVE") {
    // @ts-expect-error Looks like this is a bug in TypeScript?
    const creds: IntegrationStates["GOOGLEDRIVE"]["credentials"] =
      data.credentials;
    return {
      name: "GOOGLEDRIVE",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {
        ...creds["googledrive.linking.enabled"]
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
  if (integration === "MSTEAMS") {
    return {
      name: "MSTEAMS",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {},
    };
  }
  if (integration === "NEXTCLOUD") {
    // @ts-expect-error Looks like this is a bug in TypeScript?
    const creds: IntegrationStates["NEXTCLOUD"]["credentials"] =
      data.credentials;
    return {
      name: "NEXTCLOUD",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {
        ...creds.ACCESS_TOKEN.map((token) => ({
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
    // @ts-expect-error Looks like this is a bug in TypeScript?
    const creds: IntegrationStates["OWNCLOUD"]["credentials"] =
      data.credentials;
    return {
      name: "OWNCLOUD",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {
        ...creds.ACCESS_TOKEN.map((token) => ({
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
    // @ts-expect-error Looks like this is a bug in TypeScript?
    const creds: IntegrationStates["PYRAT"]["credentials"] = data.credentials;
    return {
      name: "PYRAT",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: Object.fromEntries(
        creds.authenticatedServers.map(({ alias, url, apiKey, optionsId }) => [
          optionsId,
          {
            PYRAT_ALIAS: alias,
            PYRAT_URL: url,
            PYRAT_APIKEY: apiKey,
          },
        ])
      ),
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
    // @ts-expect-error Looks like this is a bug in TypeScript?
    const creds: IntegrationStates["ZENODO"]["credentials"] = data.credentials;
    return {
      name: "ZENODO",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {
        ...creds.ZENODO_USER_TOKEN.map((token) => ({
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
export function useIntegrationsEndpoint(): {
  /**
   * Calls the `/allIntegrations` API endpoint.
   *
   * @returns A promise of the current state of all of the integrations.
   */
  allIntegrations: () => Promise<IntegrationStates>;

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
  update: <I extends Integration>(
    integration: I,
    newState: IntegrationStates[I]
  ) => Promise<IntegrationStates[I]>;

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
  saveAppOptions: <I extends Integration>(
    appName: I,
    optionsId: Optional<OptionsId>,
    newOption: object
  ) => Promise<IntegrationStates[I]>;

  /**
   * Calls the `deleteAppOptions` API endpoint, deleting a configuration option
   * for the integrations that support an array of configurations.
   *
   * @return If the network call fails then the returned Promise will be in a
   *         rejected state, wrapping an instance of Error. It is recommended
   *         that the caller displays an alert to indicate to the user that the
   *         action failed, displaying `error.message`.
   */
  deleteAppOptions: <I extends Integration>(
    appName: I,
    optionsId: OptionsId
  ) => Promise<void>;
} {
  const api = axios.create({
    baseURL: "/integration",
    timeout: ONE_MINUTE_IN_MS,
  });
  const { addAlert } = useContext(AlertContext);

  const allIntegrations = async (): Promise<IntegrationStates> => {
    const states = await api.get<
      | {
          success: true;
          data: { [integration in Integration]: FetchedState };
          error: null;
        }
      | {
          success: false;
          data: null;
          error: string;
        }
    >("allIntegrations");
    if (states.data.success) {
      const data = states.data.data;
      return decodeIntegrationStates(data);
    }
    throw new Error(states.data.error);
  };

  const update = React.useCallback(
    // eslint-disable-next-line complexity
    async <I extends Integration>(
      integration: I,
      newState: IntegrationStates[I]
    ): Promise<IntegrationStates[I]> => {
      try {
        const { data: responseData } = await api.post<
          | { success: true; data: FetchedState }
          | {
              success: false;
              data: null;
              errorMsg:
                | string
                | {
                    errorMessages: Array<string>;
                  };
            }
          | { errorId: string; exceptionMessage: string; tstamp: string }
        >("update", encodeIntegrationState(integration, newState));

        if (!("success" in responseData))
          throw new Error(responseData.exceptionMessage);
        if (!responseData.success) {
          if (
            !responseData.success &&
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
          throw new Error("Unknown error");
        } else {
          addAlert(
            mkAlert({
              variant: "success",
              message: "Update successful.",
            })
          );
          switch (integration) {
            case "ARGOS":
              return decodeArgos(responseData.data) as IntegrationStates[I];
            case "ASCENSCIA":
              return decodeAscenscia(responseData.data) as IntegrationStates[I];
            case "BOX":
              return decodeBox(responseData.data) as IntegrationStates[I];
            case "CHEMISTRY":
              return decodeChemistry(responseData.data) as IntegrationStates[I];
            case "CLUSTERMARKET":
              return decodeClustermarket(
                responseData.data
              ) as IntegrationStates[I];
            case "DATAVERSE":
              return decodeDataverse(responseData.data) as IntegrationStates[I];
            case "DIGITALCOMMONSDATA":
              return decodeDigitalCommonsData(
                responseData.data
              ) as IntegrationStates[I];
            case "DMPONLINE":
              return decodeDmponline(responseData.data) as IntegrationStates[I];
            case "DMPTOOL":
              return decodeDmpTool(responseData.data) as IntegrationStates[I];
            case "DROPBOX":
              return decodeDropbox(responseData.data) as IntegrationStates[I];
            case "DRYAD":
              return decodeDryad(responseData.data) as IntegrationStates[I];
            case "EGNYTE":
              return decodeEgnyte(responseData.data) as IntegrationStates[I];
            case "EVERNOTE":
              return decodeEvernote(responseData.data) as IntegrationStates[I];
            case "FIELDMARK":
              return decodeFieldmark(responseData.data) as IntegrationStates[I];
            case "FIGSHARE":
              return decodeFigshare(responseData.data) as IntegrationStates[I];
            case "GALAXY":
              return decodeGalaxy(responseData.data) as IntegrationStates[I];
            case "GITHUB":
              return decodeGitHub(responseData.data) as IntegrationStates[I];
            case "GOOGLEDRIVE":
              return decodeGoogleDrive(
                responseData.data
              ) as IntegrationStates[I];
            case "JOVE":
              return decodeJove(responseData.data) as IntegrationStates[I];
            case "MSTEAMS":
              return decodeMsTeams(responseData.data) as IntegrationStates[I];
            case "NEXTCLOUD":
              return decodeNextCloud(responseData.data) as IntegrationStates[I];
            case "OMERO":
              return decodeOmero(responseData.data) as IntegrationStates[I];
            case "ONEDRIVE":
              return decodeOneDrive(responseData.data) as IntegrationStates[I];
            case "OWNCLOUD":
              return decodeOwnCloud(responseData.data) as IntegrationStates[I];
            case "PROTOCOLS_IO":
              return decodeProtocolsIo(
                responseData.data
              ) as IntegrationStates[I];
            case "PYRAT":
              return decodePyrat(responseData.data) as IntegrationStates[I];
            case "SLACK":
              return decodeSlack(responseData.data) as IntegrationStates[I];
            case "ZENODO":
              return decodeZenodo(responseData.data) as IntegrationStates[I];
            default:
              throw new Error("Invalid integration");
          }
        }
      } catch (e) {
        if (e instanceof Error)
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

  // eslint-disable-next-line complexity
  const saveAppOptions = async <I extends Integration>(
    appName: I,
    optionsId: Optional<OptionsId>,
    newOption: object
  ): Promise<IntegrationStates[I]> => {
    const params = new URLSearchParams({
      appName,
      ...optionsId.map((o) => ({ optionsId: o })).orElse({}),
    });
    const response = await api.post<
      | { success: true; data: FetchedState }
      | { success: false; data: null; errorMsg: string }
      | { errorId: string; exceptionMessage: string; tstamp: string }
    >("saveAppOptions", newOption, { params });

    if (!("success" in response.data)) {
      throw new Error(response.data.exceptionMessage);
    }
    if (!response.data.success) {
      if (
        response.data.errorMsg !== null &&
        typeof response.data.errorMsg !== "undefined"
      ) {
        throw new Error(response.data.errorMsg);
      }
      throw new Error("Unknown reason");
    } else {
      switch (appName) {
        case "ARGOS":
          return decodeArgos(response.data.data) as IntegrationStates[I];
        case "BOX":
          return decodeBox(response.data.data) as IntegrationStates[I];
        case "CHEMISTRY":
          return decodeChemistry(response.data.data) as IntegrationStates[I];
        case "CLUSTERMARKET":
          return decodeClustermarket(
            response.data.data
          ) as IntegrationStates[I];
        case "DATAVERSE":
          return decodeDataverse(response.data.data) as IntegrationStates[I];
        case "DIGITALCOMMONSDATA":
          return decodeDigitalCommonsData(
            response.data.data
          ) as IntegrationStates[I];
        case "DMPONLINE":
          return decodeDmponline(response.data.data) as IntegrationStates[I];
        case "DMPTOOL":
          return decodeDmpTool(response.data.data) as IntegrationStates[I];
        case "DROPBOX":
          return decodeDropbox(response.data.data) as IntegrationStates[I];
        case "DRYAD":
          return decodeDryad(response.data.data) as IntegrationStates[I];
        case "EGNYTE":
          return decodeEgnyte(response.data.data) as IntegrationStates[I];
        case "EVERNOTE":
          return decodeEvernote(response.data.data) as IntegrationStates[I];
        case "FIELDMARK":
          return decodeFieldmark(response.data.data) as IntegrationStates[I];
        case "FIGSHARE":
          return decodeFigshare(response.data.data) as IntegrationStates[I];
        case "GALAXY":
          return decodeGalaxy(response.data.data) as IntegrationStates[I];
        case "GITHUB":
          return decodeGitHub(response.data.data) as IntegrationStates[I];
        case "GOOGLEDRIVE":
          return decodeGoogleDrive(response.data.data) as IntegrationStates[I];
        case "JOVE":
          return decodeJove(response.data.data) as IntegrationStates[I];
        case "MSTEAMS":
          return decodeMsTeams(response.data.data) as IntegrationStates[I];
        case "NEXTCLOUD":
          return decodeNextCloud(response.data.data) as IntegrationStates[I];
        case "OMERO":
          return decodeOmero(response.data.data) as IntegrationStates[I];
        case "ONEDRIVE":
          return decodeOneDrive(response.data.data) as IntegrationStates[I];
        case "OWNCLOUD":
          return decodeOwnCloud(response.data.data) as IntegrationStates[I];
        case "PROTOCOLS_IO":
          return decodeProtocolsIo(response.data.data) as IntegrationStates[I];
        case "PYRAT":
          return decodePyrat(response.data.data) as IntegrationStates[I];
        case "SLACK":
          return decodeSlack(response.data.data) as IntegrationStates[I];
        case "ZENODO":
          return decodeZenodo(response.data.data) as IntegrationStates[I];
        default:
          throw new Error("Invalid integration");
      }
    }
  };

  const deleteAppOptions = async <I extends Integration>(
    appName: I,
    optionsId: OptionsId
  ): Promise<void> => {
    const formData = new FormData();
    formData.append("optionsId", `${optionsId}`);
    const response = await api.post<
      | { success: true; data: FetchedState }
      | { success: false; data: null; errorMsg: string }
      | { errorId: string; exceptionMessage: string; tstamp: string }
    >("deleteAppOptions", formData, {
      params: new URLSearchParams({ appName }),
    });

    if (!("success" in response.data)) {
      throw new Error(response.data.exceptionMessage);
    }
    if (!response.data.success) {
      throw new Error(response.data.errorMsg);
    }
  };

  return { allIntegrations, update, saveAppOptions, deleteAppOptions };
}
