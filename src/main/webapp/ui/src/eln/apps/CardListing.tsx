import Grid from "@mui/material/Grid";
import Typography from "@mui/material/Typography";
import { runInAction } from "mobx";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import DSW from "@/eln/apps/integrations/DSW";
import RaidIntegrationCard from "@/eln/apps/integrations/Raid/RaidIntegrationCard";
import CardPlacementContext from "./CardPlacementContext";
import ApiDirect from "./integrations/ApiDirect";
import Argos from "./integrations/Argos";
import Box from "./integrations/Box";
import Chemistry from "./integrations/Chemistry";
import Clustermarket from "./integrations/Clustermarket";
import Dataverse from "./integrations/Dataverse";
import DigitalCommonsData from "./integrations/DigitalCommonsData";
import DMPAssistant from "./integrations/DMPAssistant";
import DMPonline from "./integrations/DMPonline";
import DMPTool from "./integrations/DMPTool";
import Dropbox from "./integrations/Dropbox";
import Dryad from "./integrations/Dryad";
import Egnyte from "./integrations/Egnyte";
import Fieldmark from "./integrations/Fieldmark";
import Figshare from "./integrations/Figshare";
import Galaxy from "./integrations/Galaxy";
import GitHub from "./integrations/GitHub";
import GoogleDrive from "./integrations/GoogleDrive";
import Jove from "./integrations/Jove";
import Jupyter from "./integrations/Jupyter";
import MSTeams from "./integrations/MSTeams";
import NextCloud from "./integrations/NextCloud";
import Omero from "./integrations/Omero";
import OneDrive from "./integrations/OneDrive";
import OwnCloud from "./integrations/OwnCloud";
import ProtocolsIO from "./integrations/ProtocolsIO";
import Pyrat from "./integrations/Pyrat";
import Slack from "./integrations/Slack";
import Zenodo from "./integrations/Zenodo";
import { type IntegrationState, type IntegrationStates, useIntegrationsEndpoint } from "./useIntegrationsEndpoint";

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

function CardListing({ mode, integrationStates }: CardListingArgs): React.ReactNode {
  const { t } = useTranslation("apps");
  const { update } = useIntegrationsEndpoint();
  const { frozenModes } = React.useContext(CardPlacementContext);
  const isShownHere = (integration: keyof IntegrationStates) =>
    (frozenModes?.[integration] ?? integrationStates[integration].mode) === mode;

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
    [update, integrationStates.ARGOS],
  );

  const boxUpdate = React.useCallback(
    (newState: IntegrationStates["BOX"]) => {
      void runInAction(async () => {
        integrationStates.BOX = await update("BOX", newState);
      });
    },
    [update, integrationStates.BOX],
  );

  const clustermarketUpdate = React.useCallback(
    (newState: IntegrationStates["CLUSTERMARKET"]) => {
      void runInAction(async () => {
        integrationStates.CLUSTERMARKET = await update("CLUSTERMARKET", newState);
      });
    },
    [update, integrationStates.CLUSTERMARKET],
  );

  const dataverseUpdate = React.useCallback(
    (newState: IntegrationStates["DATAVERSE"]) => {
      void runInAction(async () => {
        integrationStates.DATAVERSE = await update("DATAVERSE", newState);
      });
    },
    [update, integrationStates.DATAVERSE],
  );

  const digitalCommonsDataUpdate = React.useCallback(
    (newState: IntegrationStates["DIGITALCOMMONSDATA"]) => {
      void runInAction(async () => {
        integrationStates.DIGITALCOMMONSDATA = await update("DIGITALCOMMONSDATA", newState);
      });
    },
    [update, integrationStates.DIGITALCOMMONSDATA],
  );

  const dmpassistantUpdate = React.useCallback(
    (newState: IntegrationStates["DMPASSISTANT"]) => {
      void runInAction(async () => {
        integrationStates.DMPASSISTANT = await update("DMPASSISTANT", newState);
      });
    },
    [update, integrationStates.DMPASSISTANT],
  );

  const dmponlineUpdate = React.useCallback(
    (newState: IntegrationStates["DMPONLINE"]) => {
      void runInAction(async () => {
        integrationStates.DMPONLINE = await update("DMPONLINE", newState);
      });
    },
    [update, integrationStates.DMPONLINE],
  );

  const dmptoolUpdate = React.useCallback(
    (newState: IntegrationStates["DMPTOOL"]) => {
      void runInAction(async () => {
        integrationStates.DMPTOOL = await update("DMPTOOL", newState);
      });
    },
    [update, integrationStates.DMPTOOL],
  );

  const dropboxUpdate = React.useCallback(
    (newState: IntegrationStates["DROPBOX"]) => {
      void runInAction(async () => {
        integrationStates.DROPBOX = await update("DROPBOX", newState);
      });
    },
    [update, integrationStates.DROPBOX],
  );

  const dryadUpdate = React.useCallback(
    (newState: IntegrationStates["DRYAD"]) => {
      void runInAction(async () => {
        integrationStates.DRYAD = await update("DRYAD", newState);
      });
    },
    [update, integrationStates.DRYAD],
  );

  const dswUpdate = React.useCallback(
    (newState: IntegrationStates["DSW"]) => {
      void runInAction(async () => {
        integrationStates.DSW = await update("DSW", newState);
      });
    },
    [update, integrationStates.DSW],
  );

  const egnyteUpdate = React.useCallback(
    (newState: IntegrationStates["EGNYTE"]) => {
      void runInAction(async () => {
        integrationStates.EGNYTE = await update("EGNYTE", newState);
      });
    },
    [update, integrationStates.EGNYTE],
  );

  const fieldmarkUpdate = React.useCallback(
    (newState: IntegrationStates["FIELDMARK"]) => {
      void runInAction(async () => {
        integrationStates.FIELDMARK = await update("FIELDMARK", newState);
      });
    },
    [update, integrationStates.FIELDMARK],
  );

  const figshareUpdate = React.useCallback(
    (newState: IntegrationStates["FIGSHARE"]) => {
      void runInAction(async () => {
        integrationStates.FIGSHARE = await update("FIGSHARE", newState);
      });
    },
    [update, integrationStates.FIGSHARE],
  );

  const galaxyUpdate = React.useCallback(
    (newState: IntegrationStates["GALAXY"]) => {
      void runInAction(async () => {
        integrationStates.GALAXY = await update("GALAXY", newState);
      });
    },
    [update, integrationStates.GALAXY],
  );

  const githubUpdate = React.useCallback(
    (newState: IntegrationStates["GITHUB"]) => {
      void runInAction(async () => {
        integrationStates.GITHUB = await update("GITHUB", newState);
      });
    },
    [update, integrationStates.GITHUB],
  );

  const googleDriveUpdate = React.useCallback(
    (newState: IntegrationStates["GOOGLEDRIVE"]) => {
      void runInAction(async () => {
        integrationStates.GOOGLEDRIVE = await update("GOOGLEDRIVE", newState);
      });
    },
    [update, integrationStates.GOOGLEDRIVE],
  );

  const chemistryUpdate = React.useCallback(
    (newState: IntegrationStates["CHEMISTRY"]) => {
      void runInAction(async () => {
        integrationStates.CHEMISTRY = await update("CHEMISTRY", newState);
      });
    },
    [update, integrationStates.CHEMISTRY],
  );

  const nextCloudUpdate = React.useCallback(
    (newState: IntegrationStates["NEXTCLOUD"]) => {
      void runInAction(async () => {
        integrationStates.NEXTCLOUD = await update("NEXTCLOUD", newState);
      });
    },
    [update, integrationStates.NEXTCLOUD],
  );

  const omeroUpdate = React.useCallback(
    (newState: IntegrationStates["OMERO"]) => {
      void runInAction(async () => {
        integrationStates.OMERO = await update("OMERO", newState);
      });
    },
    [update, integrationStates.OMERO],
  );

  const onedriveUpdate = React.useCallback(
    (newState: IntegrationStates["ONEDRIVE"]) => {
      void runInAction(async () => {
        integrationStates.ONEDRIVE = await update("ONEDRIVE", newState);
      });
    },
    [update, integrationStates.ONEDRIVE],
  );

  const ownCloudUpdate = React.useCallback(
    (newState: IntegrationStates["OWNCLOUD"]) => {
      void runInAction(async () => {
        integrationStates.OWNCLOUD = await update("OWNCLOUD", newState);
      });
    },
    [update, integrationStates.OWNCLOUD],
  );

  const protocolsioUpdate = React.useCallback(
    (newState: IntegrationStates["PROTOCOLS_IO"]) => {
      void runInAction(async () => {
        integrationStates.PROTOCOLS_IO = await update("PROTOCOLS_IO", newState);
      });
    },
    [update, integrationStates.PROTOCOLS_IO],
  );

  const pyratUpdate = React.useCallback(
    (newState: IntegrationStates["PYRAT"]) => {
      void runInAction(async () => {
        integrationStates.PYRAT = await update("PYRAT", newState);
      });
    },
    [update, integrationStates.PYRAT],
  );

  const raidUpdate = React.useCallback(
    (newState: IntegrationStates["RAID"]) => {
      void runInAction(async () => {
        integrationStates.RAID = await update("RAID", newState);
      });
    },
    [update, integrationStates.RAID],
  );

  const slackUpdate = React.useCallback(
    (newState: IntegrationStates["SLACK"]) => {
      void runInAction(async () => {
        integrationStates.SLACK = await update("SLACK", newState);
      });
    },
    [update, integrationStates.SLACK],
  );

  const teamsUpdate = React.useCallback(
    (newState: IntegrationStates["MSTEAMS"]) => {
      void runInAction(async () => {
        integrationStates.MSTEAMS = await update("MSTEAMS", newState);
      });
    },
    [update, integrationStates.MSTEAMS],
  );

  const zenodoUpdate = React.useCallback(
    (newState: IntegrationStates["ZENODO"]) => {
      void runInAction(async () => {
        integrationStates.ZENODO = await update("ZENODO", newState);
      });
    },
    [update, integrationStates.ZENODO],
  );

  if ((Object.keys(integrationStates) as Array<keyof IntegrationStates>).every((key) => !isShownHere(key))) {
    return <Typography variant="body1">{t("page.nothingHere")}</Typography>;
  }

  /*
   * Do note that this listing is alphabetical based on what would be intuitive
   * to a user looking for a specific service. Prefer the name of the
   * product/service over the company that provides it.
   */
  return (
    <Grid container spacing={3} sx={{ alignItems: "stretch" }}>
      {isShownHere("ARGOS") && <Argos integrationState={integrationStates.ARGOS} update={argosUpdate} />}
      {isShownHere("API_DIRECT") && <ApiDirect />}
      {isShownHere("API_DIRECT") && <Jupyter />}
      {isShownHere("API_DIRECT") && <Jove />}
      {isShownHere("BOX") && <Box integrationState={integrationStates.BOX} update={boxUpdate} />}
      {isShownHere("CHEMISTRY") && (
        <Chemistry integrationState={integrationStates.CHEMISTRY} update={chemistryUpdate} />
      )}
      {isShownHere("CLUSTERMARKET") && (
        <Clustermarket integrationState={integrationStates.CLUSTERMARKET} update={clustermarketUpdate} />
      )}
      {isShownHere("DATAVERSE") && (
        <Dataverse integrationState={integrationStates.DATAVERSE} update={dataverseUpdate} />
      )}
      {isShownHere("DIGITALCOMMONSDATA") && (
        <DigitalCommonsData integrationState={integrationStates.DIGITALCOMMONSDATA} update={digitalCommonsDataUpdate} />
      )}
      {isShownHere("DMPASSISTANT") && (
        <DMPAssistant integrationState={integrationStates.DMPASSISTANT} update={dmpassistantUpdate} />
      )}
      {isShownHere("DMPONLINE") && (
        <DMPonline integrationState={integrationStates.DMPONLINE} update={dmponlineUpdate} />
      )}
      {isShownHere("DMPTOOL") && <DMPTool integrationState={integrationStates.DMPTOOL} update={dmptoolUpdate} />}
      {isShownHere("DROPBOX") && <Dropbox integrationState={integrationStates.DROPBOX} update={dropboxUpdate} />}
      {isShownHere("DRYAD") && <Dryad integrationState={integrationStates.DRYAD} update={dryadUpdate} />}
      {isShownHere("DSW") && <DSW integrationState={integrationStates.DSW} update={dswUpdate} />}
      {isShownHere("EGNYTE") && <Egnyte integrationState={integrationStates.EGNYTE} update={egnyteUpdate} />}
      {isShownHere("FIELDMARK") && (
        <Fieldmark integrationState={integrationStates.FIELDMARK} update={fieldmarkUpdate} />
      )}
      {isShownHere("FIGSHARE") && <Figshare integrationState={integrationStates.FIGSHARE} update={figshareUpdate} />}
      {isShownHere("GALAXY") && <Galaxy integrationState={integrationStates.GALAXY} update={galaxyUpdate} />}
      {isShownHere("GITHUB") && <GitHub integrationState={integrationStates.GITHUB} update={githubUpdate} />}
      {isShownHere("GOOGLEDRIVE") && (
        <GoogleDrive integrationState={integrationStates.GOOGLEDRIVE} update={googleDriveUpdate} />
      )}
      {isShownHere("NEXTCLOUD") && (
        <NextCloud integrationState={integrationStates.NEXTCLOUD} update={nextCloudUpdate} />
      )}
      {isShownHere("OMERO") && <Omero integrationState={integrationStates.OMERO} update={omeroUpdate} />}
      {isShownHere("ONEDRIVE") && <OneDrive integrationState={integrationStates.ONEDRIVE} update={onedriveUpdate} />}
      {isShownHere("OWNCLOUD") && <OwnCloud integrationState={integrationStates.OWNCLOUD} update={ownCloudUpdate} />}
      {isShownHere("PROTOCOLS_IO") && (
        <ProtocolsIO integrationState={integrationStates.PROTOCOLS_IO} update={protocolsioUpdate} />
      )}
      {isShownHere("PYRAT") && <Pyrat integrationState={integrationStates.PYRAT} update={pyratUpdate} />}
      {isShownHere("RAID") && <RaidIntegrationCard integrationState={integrationStates.RAID} update={raidUpdate} />}
      {isShownHere("SLACK") && <Slack integrationState={integrationStates.SLACK} update={slackUpdate} />}
      {isShownHere("MSTEAMS") && <MSTeams integrationState={integrationStates.MSTEAMS} update={teamsUpdate} />}
      {isShownHere("ZENODO") && <Zenodo integrationState={integrationStates.ZENODO} update={zenodoUpdate} />}
    </Grid>
  );
}

export default observer(CardListing);
