import React from "react";
import {
  type IntegrationStates,
  type IntegrationState,
  useIntegrationsEndpoint,
} from "./useIntegrationsEndpoint";
import Grid from "@mui/material/Grid";
import ApiDirect from "./integrations/ApiDirect";
import Argos from "./integrations/Argos";
import Ascenscia from "./integrations/Ascenscia";
import Box from "./integrations/Box";
import Chemistry from "./integrations/Chemistry";
import Clustermarket from "./integrations/Clustermarket";
import Dataverse from "./integrations/Dataverse";
import DigitalCommonsData from "./integrations/DigitalCommonsData";
import DMPonline from "./integrations/DMPonline";
import DMPTool from "./integrations/DMPTool";
import Dropbox from "./integrations/Dropbox";
import Dryad from "./integrations/Dryad";
import Egnyte from "./integrations/Egnyte";
import Evernote from "./integrations/Evernote";
import Fieldmark from "./integrations/Fieldmark";
import Figshare from "./integrations/Figshare";
import GitHub from "./integrations/GitHub";
import GoogleDrive from "./integrations/GoogleDrive";
import Jove from "./integrations/Jove";
import MSTeams from "./integrations/MSTeams";
import NextCloud from "./integrations/NextCloud";
import Omero from "./integrations/Omero";
import OneDrive from "./integrations/OneDrive";
import OwnCloud from "./integrations/OwnCloud";
import ProtocolsIO from "./integrations/ProtocolsIO";
import Pyrat from "./integrations/Pyrat";
import Slack from "./integrations/Slack";
import Zenodo from "./integrations/Zenodo";
import { observer } from "mobx-react-lite";
import Typography from "@mui/material/Typography";
import { runInAction } from "mobx";

type CardListingArgs = {
  /*
   * This prop determines which integrationStates.are shown in this listing. It is
   * passed to each integration's card which will only render themselves if the
   * integration's current mode matches this value.
   */
  mode: IntegrationState<unknown>["mode"];

  /*
   * This is a mapping of integrationStates.to their current mode, as exposed by
   * the useIntegrationsEndpoint custom hook.
   */
  integrationStates: IntegrationStates;
};

//eslint-disable-next-line complexity
function CardListing({
  mode,
  integrationStates,
}: CardListingArgs): React.ReactNode {
  const { update } = useIntegrationsEndpoint();

  /*
   * These memoised functions mean that when one integration is modified
   * the rest don't need to re-render
   */

  const argosUpdate = React.useCallback(
    (newState: IntegrationStates["ARGOS"]) => {
      void runInAction(async () => {
        integrationStates.ARGOS = await update("ARGOS", newState);
      });
    },
    //eslint-disable-next-line react-hooks/exhaustive-deps
    [update]
  );

  const ascensciaUpdate = React.useCallback(
    (newState: IntegrationStates["ASCENSCIA"]) => {
      void runInAction(async () => {
        integrationStates.ASCENSCIA = await update("ASCENSCIA", newState);
      });
    },
    //eslint-disable-next-line react-hooks/exhaustive-deps
    [update]
  );

  const boxUpdate = React.useCallback(
    (newState: IntegrationStates["BOX"]) => {
      void runInAction(async () => {
        integrationStates.BOX = await update("BOX", newState);
      });
    },
    //eslint-disable-next-line react-hooks/exhaustive-deps
    [update]
  );

  const clustermarketUpdate = React.useCallback(
    (newState: IntegrationStates["CLUSTERMARKET"]) => {
      void runInAction(async () => {
        integrationStates.CLUSTERMARKET = await update(
          "CLUSTERMARKET",
          newState
        );
      });
    },
    //eslint-disable-next-line react-hooks/exhaustive-deps
    [update]
  );

  const dataverseUpdate = React.useCallback(
    (newState: IntegrationStates["DATAVERSE"]) => {
      void runInAction(async () => {
        integrationStates.DATAVERSE = await update("DATAVERSE", newState);
      });
    },
    //eslint-disable-next-line react-hooks/exhaustive-deps
    [update]
  );

  const digitalCommonsDataUpdate = React.useCallback(
    (newState: IntegrationStates["DIGITALCOMMONSDATA"]) => {
      void runInAction(async () => {
        integrationStates.DIGITALCOMMONSDATA = await update(
          "DIGITALCOMMONSDATA",
          newState
        );
      });
    },
    //eslint-disable-next-line react-hooks/exhaustive-deps
    [update]
  );

  const dmponlineUpdate = React.useCallback(
    (newState: IntegrationStates["DMPONLINE"]) => {
      void runInAction(async () => {
        integrationStates.DMPONLINE = await update("DMPONLINE", newState);
      });
    },
    [update]
  );

  const dmptoolUpdate = React.useCallback(
    (newState: IntegrationStates["DMPTOOL"]) => {
      void runInAction(async () => {
        integrationStates.DMPTOOL = await update("DMPTOOL", newState);
      });
    },
    //eslint-disable-next-line react-hooks/exhaustive-deps
    [update]
  );

  const dropboxUpdate = React.useCallback(
    (newState: IntegrationStates["DROPBOX"]) => {
      void runInAction(async () => {
        integrationStates.DROPBOX = await update("DROPBOX", newState);
      });
    },
    //eslint-disable-next-line react-hooks/exhaustive-deps
    [update]
  );

  const dryadUpdate = React.useCallback(
    (newState: IntegrationStates["DRYAD"]) => {
      void runInAction(async () => {
        integrationStates.DRYAD = await update("DRYAD", newState);
      });
    },
    //eslint-disable-next-line react-hooks/exhaustive-deps
    [update]
  );

  const egnyteUpdate = React.useCallback(
    (newState: IntegrationStates["EGNYTE"]) => {
      void runInAction(async () => {
        integrationStates.EGNYTE = await update("EGNYTE", newState);
      });
    },
    //eslint-disable-next-line react-hooks/exhaustive-deps
    [update]
  );

  const evernoteUpdate = React.useCallback(
    (newState: IntegrationStates["EVERNOTE"]) => {
      void runInAction(async () => {
        integrationStates.EVERNOTE = await update("EVERNOTE", newState);
      });
    },
    //eslint-disable-next-line react-hooks/exhaustive-deps
    [update]
  );

  const fieldmarkUpdate = React.useCallback(
    (newState: IntegrationStates["FIELDMARK"]) => {
      void runInAction(async () => {
        integrationStates.FIELDMARK = await update("FIELDMARK", newState);
      });
    },
    //eslint-disable-next-line react-hooks/exhaustive-deps
    [update]
  );

  const figshareUpdate = React.useCallback(
    (newState: IntegrationStates["FIGSHARE"]) => {
      void runInAction(async () => {
        integrationStates.FIGSHARE = await update("FIGSHARE", newState);
      });
    },
    //eslint-disable-next-line react-hooks/exhaustive-deps
    [update]
  );

  const githubUpdate = React.useCallback(
    (newState: IntegrationStates["GITHUB"]) => {
      void runInAction(async () => {
        integrationStates.GITHUB = await update("GITHUB", newState);
      });
    },
    //eslint-disable-next-line react-hooks/exhaustive-deps
    [update]
  );

  const googleDriveUpdate = React.useCallback(
    (newState: IntegrationStates["GOOGLEDRIVE"]) => {
      void runInAction(async () => {
        integrationStates.GOOGLEDRIVE = await update("GOOGLEDRIVE", newState);
      });
    },
    //eslint-disable-next-line react-hooks/exhaustive-deps
    [update]
  );

  const joveUpdate = React.useCallback(
    (newState: IntegrationStates["JOVE"]) => {
      void runInAction(async () => {
        integrationStates.JOVE = await update("JOVE", newState);
      });
    },
    //eslint-disable-next-line react-hooks/exhaustive-deps
    [update]
  );

  const chemistryUpdate = React.useCallback(
    (newState: IntegrationStates["CHEMISTRY"]) => {
      void runInAction(async () => {
        integrationStates.CHEMISTRY = await update("CHEMISTRY", newState);
      });
    },
    //eslint-disable-next-line react-hooks/exhaustive-deps
    [update]
  );

  const nextCloudUpdate = React.useCallback(
    (newState: IntegrationStates["NEXTCLOUD"]) => {
      void runInAction(async () => {
        integrationStates.NEXTCLOUD = await update("NEXTCLOUD", newState);
      });
    },
    //eslint-disable-next-line react-hooks/exhaustive-deps
    [update]
  );

  const omeroUpdate = React.useCallback(
    (newState: IntegrationStates["OMERO"]) => {
      void runInAction(async () => {
        integrationStates.OMERO = await update("OMERO", newState);
      });
    },
    //eslint-disable-next-line react-hooks/exhaustive-deps
    [update]
  );

  const onedriveUpdate = React.useCallback(
    (newState: IntegrationStates["ONEDRIVE"]) => {
      void runInAction(async () => {
        integrationStates.ONEDRIVE = await update("ONEDRIVE", newState);
      });
    },
    //eslint-disable-next-line react-hooks/exhaustive-deps
    [update]
  );

  const ownCloudUpdate = React.useCallback(
    (newState: IntegrationStates["OWNCLOUD"]) => {
      void runInAction(async () => {
        integrationStates.OWNCLOUD = await update("OWNCLOUD", newState);
      });
    },
    //eslint-disable-next-line react-hooks/exhaustive-deps
    [update]
  );

  const protocolsioUpdate = React.useCallback(
    (newState: IntegrationStates["PROTOCOLS_IO"]) => {
      void runInAction(async () => {
        integrationStates.PROTOCOLS_IO = await update("PROTOCOLS_IO", newState);
      });
    },
    //eslint-disable-next-line react-hooks/exhaustive-deps
    [update]
  );

  const pyratUpdate = React.useCallback(
    (newState: IntegrationStates["PYRAT"]) => {
      void runInAction(async () => {
        integrationStates.PYRAT = await update("PYRAT", newState);
      });
    },
    //eslint-disable-next-line react-hooks/exhaustive-deps
    [update]
  );

  const slackUpdate = React.useCallback(
    (newState: IntegrationStates["SLACK"]) => {
      void runInAction(async () => {
        integrationStates.SLACK = await update("SLACK", newState);
      });
    },
    //eslint-disable-next-line react-hooks/exhaustive-deps
    [update]
  );

  const teamsUpdate = React.useCallback(
    (newState: IntegrationStates["MSTEAMS"]) => {
      void runInAction(async () => {
        integrationStates.MSTEAMS = await update("MSTEAMS", newState);
      });
    },
    //eslint-disable-next-line react-hooks/exhaustive-deps
    [update]
  );

  const zenodoUpdate = React.useCallback(
    (newState: IntegrationStates["ZENODO"]) => {
      void runInAction(async () => {
        integrationStates.ZENODO = await update("ZENODO", newState);
      });
    },
    //eslint-disable-next-line react-hooks/exhaustive-deps
    [update]
  );

  if (
    Object.values(integrationStates)
      .map((s) => s.mode)
      .filter((m) => m === mode).length === 0
  ) {
    return <Typography variant="body1">Nothing here!</Typography>;
  }

  /*
   * Do note that this listing is alphabetical based on what would be intuitive
   * to a user looking for a specific service. Prefer the name of the
   * product/service over the company that provides it.
   */
  return (
    <Grid container spacing={3} alignItems="stretch">
      {integrationStates.ARGOS.mode === mode && (
        <Argos
          integrationState={integrationStates.ARGOS}
          update={argosUpdate}
        />
      )}
      {integrationStates.API_DIRECT.mode === mode && <ApiDirect />}
      {integrationStates.ASCENSCIA.mode === mode && <Ascenscia />}
      {integrationStates.ASCENSCIA.mode === mode && (
        <Ascenscia
          integrationState={integrationStates.ASCENSCIA}
          update={ascensciaUpdate}
        />
      )}
      {integrationStates.BOX.mode === mode && (
        <Box integrationState={integrationStates.BOX} update={boxUpdate} />
      )}
      {integrationStates.CLUSTERMARKET.mode === mode && (
        <Clustermarket
          integrationState={integrationStates.CLUSTERMARKET}
          update={clustermarketUpdate}
        />
      )}
      {integrationStates.DATAVERSE.mode === mode && (
        <Dataverse
          integrationState={integrationStates.DATAVERSE}
          update={dataverseUpdate}
        />
      )}
      {integrationStates.DIGITALCOMMONSDATA.mode === mode && (
        <DigitalCommonsData
          integrationState={integrationStates.DIGITALCOMMONSDATA}
          update={digitalCommonsDataUpdate}
        />
      )}
      {integrationStates.DMPONLINE.mode === mode && (
        <DMPonline
          integrationState={integrationStates.DMPONLINE}
          update={dmponlineUpdate}
        />
      )}
      {integrationStates.DMPTOOL.mode === mode && (
        <DMPTool
          integrationState={integrationStates.DMPTOOL}
          update={dmptoolUpdate}
        />
      )}
      {integrationStates.DROPBOX.mode === mode && (
        <Dropbox
          integrationState={integrationStates.DROPBOX}
          update={dropboxUpdate}
        />
      )}
      {integrationStates.DRYAD.mode === mode && (
        <Dryad
          integrationState={integrationStates.DRYAD}
          update={dryadUpdate}
        />
      )}
      {integrationStates.EGNYTE.mode === mode && (
        <Egnyte
          integrationState={integrationStates.EGNYTE}
          update={egnyteUpdate}
        />
      )}
      {integrationStates.EVERNOTE.mode === mode && (
        <Evernote
          integrationState={integrationStates.EVERNOTE}
          update={evernoteUpdate}
        />
      )}
      {integrationStates.FIELDMARK.mode === mode && (
        <Fieldmark
          integrationState={integrationStates.FIELDMARK}
          update={fieldmarkUpdate}
        />
      )}
      {integrationStates.FIGSHARE.mode === mode && (
        <Figshare
          integrationState={integrationStates.FIGSHARE}
          update={figshareUpdate}
        />
      )}
      {integrationStates.GITHUB.mode === mode && (
        <GitHub
          integrationState={integrationStates.GITHUB}
          update={githubUpdate}
        />
      )}
      {integrationStates.GOOGLEDRIVE.mode === mode && (
        <GoogleDrive
          integrationState={integrationStates.GOOGLEDRIVE}
          update={googleDriveUpdate}
        />
      )}
      {integrationStates.JOVE.mode === mode && (
        <Jove integrationState={integrationStates.JOVE} update={joveUpdate} />
      )}
      {integrationStates.CHEMISTRY.mode === mode && (
        <Chemistry
          integrationState={integrationStates.CHEMISTRY}
          update={chemistryUpdate}
        />
      )}
      {integrationStates.NEXTCLOUD.mode === mode && (
        <NextCloud
          integrationState={integrationStates.NEXTCLOUD}
          update={nextCloudUpdate}
        />
      )}
      {integrationStates.OMERO.mode === mode && (
        <Omero
          integrationState={integrationStates.OMERO}
          update={omeroUpdate}
        />
      )}
      {integrationStates.ONEDRIVE.mode === mode && (
        <OneDrive
          integrationState={integrationStates.ONEDRIVE}
          update={onedriveUpdate}
        />
      )}
      {integrationStates.OWNCLOUD.mode === mode && (
        <OwnCloud
          integrationState={integrationStates.OWNCLOUD}
          update={ownCloudUpdate}
        />
      )}
      {integrationStates.PROTOCOLS_IO.mode === mode && (
        <ProtocolsIO
          integrationState={integrationStates.PROTOCOLS_IO}
          update={protocolsioUpdate}
        />
      )}
      {integrationStates.PYRAT.mode === mode && (
        <Pyrat
          integrationState={integrationStates.PYRAT}
          update={pyratUpdate}
        />
      )}
      {integrationStates.SLACK.mode === mode && (
        <Slack
          integrationState={integrationStates.SLACK}
          update={slackUpdate}
        />
      )}
      {integrationStates.MSTEAMS.mode === mode && (
        <MSTeams
          integrationState={integrationStates.MSTEAMS}
          update={teamsUpdate}
        />
      )}
      {integrationStates.ZENODO.mode === mode && (
        <Zenodo
          integrationState={integrationStates.ZENODO}
          update={zenodoUpdate}
        />
      )}
    </Grid>
  );
}

export default observer(CardListing);
