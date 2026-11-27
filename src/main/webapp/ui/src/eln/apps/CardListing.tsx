import Grid from "@mui/material/Grid";
import Typography from "@mui/material/Typography";
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
import type { IntegrationModes } from "./queries";
import type { Integration, IntegrationState } from "./useIntegrationsEndpoint";

type CardListingArgs = {
  /*
   * This prop determines which integrations are shown in this listing. It is
   * passed to each integration's card which will only render themselves if the
   * integration's current mode matches this value.
   */
  mode: IntegrationState<unknown>["mode"];

  /*
   * The current mode of every integration. Each card reads the rest of its
   * integration's state from the integrations query itself.
   */
  modes: IntegrationModes;
};

function CardListing({ mode, modes }: CardListingArgs): React.ReactNode {
  const { t } = useTranslation("apps");
  const { frozenModes } = React.useContext(CardPlacementContext);
  const isShownHere = (integration: Integration) => (frozenModes?.[integration] ?? modes[integration]) === mode;

  if ((Object.keys(modes) as Array<Integration>).every((key) => !isShownHere(key))) {
    return <Typography variant="body1">{t("page.nothingHere")}</Typography>;
  }

  /*
   * Do note that this listing is alphabetical based on what would be intuitive
   * to a user looking for a specific service. Prefer the name of the
   * product/service over the company that provides it.
   */
  return (
    <Grid container spacing={3} sx={{ alignItems: "stretch" }}>
      {isShownHere("ARGOS") && <Argos />}
      {isShownHere("API_DIRECT") && <ApiDirect />}
      {isShownHere("API_DIRECT") && <Jupyter />}
      {isShownHere("API_DIRECT") && <Jove />}
      {isShownHere("BOX") && <Box />}
      {isShownHere("CHEMISTRY") && <Chemistry />}
      {isShownHere("CLUSTERMARKET") && <Clustermarket />}
      {isShownHere("DATAVERSE") && <Dataverse />}
      {isShownHere("DIGITALCOMMONSDATA") && <DigitalCommonsData />}
      {isShownHere("DMPASSISTANT") && <DMPAssistant />}
      {isShownHere("DMPONLINE") && <DMPonline />}
      {isShownHere("DMPTOOL") && <DMPTool />}
      {isShownHere("DROPBOX") && <Dropbox />}
      {isShownHere("DRYAD") && <Dryad />}
      {isShownHere("DSW") && <DSW />}
      {isShownHere("EGNYTE") && <Egnyte />}
      {isShownHere("FIELDMARK") && <Fieldmark />}
      {isShownHere("FIGSHARE") && <Figshare />}
      {isShownHere("GALAXY") && <Galaxy />}
      {isShownHere("GITHUB") && <GitHub />}
      {isShownHere("GOOGLEDRIVE") && <GoogleDrive />}
      {isShownHere("NEXTCLOUD") && <NextCloud />}
      {isShownHere("OMERO") && <Omero />}
      {isShownHere("ONEDRIVE") && <OneDrive />}
      {isShownHere("OWNCLOUD") && <OwnCloud />}
      {isShownHere("PROTOCOLS_IO") && <ProtocolsIO />}
      {isShownHere("PYRAT") && <Pyrat />}
      {isShownHere("RAID") && <RaidIntegrationCard />}
      {isShownHere("SLACK") && <Slack />}
      {isShownHere("MSTEAMS") && <MSTeams />}
      {isShownHere("ZENODO") && <Zenodo />}
    </Grid>
  );
}

export default CardListing;
