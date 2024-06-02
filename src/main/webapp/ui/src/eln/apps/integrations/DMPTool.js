//@flow strict

import Grid from "@mui/material/Grid";
import React, { type Node, type AbstractComponent } from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import Button from "@mui/material/Button";
import DMPToolIcon from "../icons/dmptool.svg";

type DMPToolArgs = {|
  integrationState: IntegrationStates["DMPTOOL"],
  update: (IntegrationStates["DMPTOOL"]) => void,
|};

/*
 * DMPTool uses OAuth based authentication, as implemeted by the form below.
 */
function DMPTool({ integrationState, update }: DMPToolArgs): Node {
  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="DMPTool"
        integrationState={integrationState}
        explanatoryText="Create Data Management Plans for your research through a guided web-based tool with templates."
        image={DMPToolIcon}
        color={{
          hue: 208,
          saturation: 60,
          lightness: 65,
        }}
        update={(newMode) =>
          update({ mode: newMode, credentials: integrationState.credentials })
        }
        helpLinkText="DMPTool integration docs"
        website="dmptool.org"
        docLink="dmptool"
        usageText="You can import Data Management Plans (DMPs) from DMPTool into RSpace, and associate DMPs with repository exports. Exporting from RSpace automatically updates the DMP in DMPTool with a DOI of the repository deposit."
        setupSection={
          <>
            <ol>
              <li>
                Click on Connect to authorise RSpace to access your DMPTool
                account.
              </li>
              <li>Enable the integration.</li>
              <li>
                You can now import a DMP when in the Gallery, and associate a
                DMP with data when in the export dialog.
              </li>
            </ol>
            <form action="/apps/dmptool/connect" method="POST">
              <Button
                type="submit"
                sx={{ mt: 1 }}
                value={
                  integrationState.credentials.ACCESS_TOKEN.isPresent()
                    ? "Disconnect"
                    : "Connect"
                }
              >
                {integrationState.credentials.ACCESS_TOKEN.isPresent()
                  ? "Disconnect"
                  : "Connect"}
              </Button>
              {/*
               * Forms can only be GET or POST. This input is a hack to
               * invoke the DELETE method of the endpoint
               */}
              {integrationState.credentials.ACCESS_TOKEN.isPresent() ? (
                <input type="hidden" name="_method" value="delete" />
              ) : null}
            </form>
          </>
        }
      />
    </Grid>
  );
}

export default (React.memo(DMPTool): AbstractComponent<DMPToolArgs>);
