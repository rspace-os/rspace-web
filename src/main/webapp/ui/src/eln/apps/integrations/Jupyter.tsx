import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import React from "react";
import { LOGO_COLOR } from "@/assets/branding/Jupyter";
import JupyterIcon from "../../../assets/branding/Jupyter/logo.svg";
import IntegrationCard from "../IntegrationCard";

function Jupyter(): React.ReactNode {
  return (
    <Grid
      sx={{ display: "flex" }}
      size={{
        sm: 6,
        xs: 12,
      }}
    >
      <IntegrationCard
        name="Jupyter Notebook Synchronisation"
        explanatoryText="Save Jupyter notebooks and attached data to RSpace automatically. On each run, save version, attach updated data, and view notebooks directly in RSpace."
        image={JupyterIcon}
        color={LOGO_COLOR}
        usageText="Use RSpace python client >=v2.6.2 to send data from Jupyter Notebooks to RSpace. This allows you to use Jupyter Notebooks to create and share data-driven documents."
        helpLinkText="RSpace Jupyter Notebook documentation"
        website="docs.jupyter.org/en/latest/"
        docLink="jupyter"
        setupSection={
          // biome-ignore lint/complexity/noUselessFragments: initial biome migration
          <>
            <ol>
              <li>
                <strong>Enable API access:</strong> Generate your API key in My RSpace → My Profile.
              </li>
              <li>
                <strong>Configure Jupyter instance for all notebooks:</strong> Follow the instructions in RSpace help
                docs to use pip to install RSpace client. Run a python cell with the following code:
                <Typography variant="body2" sx={{ mt: 2, mb: 1 }}>
                  <strong>One time install step:</strong>
                  <Box
                    component="pre"
                    sx={{
                      background: "#f5f5f5",
                      padding: "8px",
                      overflowX: "auto",
                    }}
                  >
                    {`%pip install rspace-client==2.6.2`}
                  </Box>
                </Typography>
                Run the cell then <strong>restart the kernel</strong> and <strong>refresh the browser </strong> tab
                running Jupyter.
              </li>
              <li>
                <strong>Configure notebook:</strong> Follow the instructions in RSpace help docs to import the
                sync_notebook script.
                <Typography variant="body2" sx={{ mt: 2, mb: 1 }}>
                  <strong>Do this step once per notebook:</strong>
                  <Box
                    component="pre"
                    sx={{
                      background: "#f5f5f5",
                      padding: "8px",
                      overflowX: "auto",
                    }}
                  >
                    {`from rspace_client.notebook_sync import sync_notebook`}
                  </Box>
                </Typography>
                Run the cell, <strong>restart the kernel</strong> and then run the cell one more time{" "}
                <strong>without a kernel restart. Save the Notebook.</strong>
              </li>
              <li>
                <strong>Run the code:</strong>
                <Typography variant="body2" sx={{ mt: 2, mb: 1 }}>
                  Paste this code into <strong>the last cell in the notebook:</strong>
                  <Box
                    component="pre"
                    sx={{
                      background: "#f5f5f5",
                      padding: "8px",
                      overflowX: "auto",
                    }}
                  >
                    {`await sync_notebook.sync_notebook_to_rspace(
rspace_url="https://researchspace2.eu.ngrok.io/",
rspace_username="user1a")`}
                  </Box>
                </Typography>
              </li>
            </ol>
          </>
        }
        update={() => {}}
        integrationState={{
          mode: "EXTERNAL",
          credentials: null,
        }}
      />
    </Grid>
  );
}

export default React.memo(observer(Jupyter));
