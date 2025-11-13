import Grid from "@mui/material/Grid";
import React from "react";
import IntegrationCard from "../IntegrationCard";
import JupyterIcon from "../../../assets/branding/Jupyter/logo.svg";
import { observer } from "mobx-react-lite";
import { LOGO_COLOR } from "@/assets/branding/Jupyter";
import Typography from "@mui/material/Typography";

function Jupyter(): React.ReactNode {
  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
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
          <>
            <ol>
              <li> <b>Enable API access:</b> Generate your API key in My RSpace → My Profile.</li>
              <li>
                <b>Configure Jupyter instance for all notebooks:</b> Follow the instructions in RSpace help docs to use pip to install RSpace client. Run a python cell with the following code:
                <Typography variant="body2" sx={{ mt: 2, mb: 1 }}>
                  <strong>One time install step:</strong>
                  <pre
                      style={{
                        background: "#f5f5f5",
                        padding: "8px",
                        overflowX: "auto",
                      }}
                  >
            {`
%pip install rspace-client==2.6.2
            
            `}
              </pre>
                </Typography>
                <b>Run the cell then restart the kernel AND REFRESH THE BROWSER TAB RUNNING JUPYTER.</b>
              </li>
              <li>
                <b>Configure notebook:</b> Follow the instructions in RSpace help docs to import the sync_notebook script.
            <Typography variant="body2" sx={{ mt: 2, mb: 1 }}>
              <strong>Do this step once per notebook:</strong>
              <pre
                style={{
                  background: "#f5f5f5",
                  padding: "8px",
                  overflowX: "auto",
                }}
              >
                {`
from rspace_client.notebook_sync import sync_notebook

`}
              </pre>
            </Typography>
                <b>Run the cell, restart the kernel and then run the cell one more time without a kernel restart. Save the Notebook.</b>
              </li>
              <li>
                <b>Run the code:</b>
                <Typography variant="body2" sx={{ mt: 2, mb: 1 }}>
                  <strong>Paste this code into the last cell in the notebook:</strong>
                  <pre
                      style={{
                        background: "#f5f5f5",
                        padding: "8px",
                        overflowX: "auto",
                      }}
                  >
                {`
await sync_notebook.sync_notebook_to_rspace(
rspace_url="https://researchspace2.eu.ngrok.io/",
rspace_username="user1a")
`}
              </pre>
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
