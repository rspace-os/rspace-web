import Grid from "@mui/material/Grid";
import React from "react";
import IntegrationCard from "../IntegrationCard";
import ApiIcon from "../../../assets/graphics/ApiIcon";
import { observer } from "mobx-react-lite";
import { ACCENT_COLOR } from "../../../assets/branding/rspace/other";
import Typography from "@mui/material/Typography";

function ApiDirect(): React.ReactNode {
  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="API Direct Access"
        explanatoryText="Directly interact with the RSpace API using scripts and custom applications."
        image={ApiIcon}
        color={ACCENT_COLOR.main}
        usageText="Access RSpace programmatically using Python, R, or any language with HTTP capabilities to automate tasks and integrate with your own tools."
        helpLinkText="API documentation"
        website="/public/apiDocs"
        docLink="apiDirect"
        setupSection={
          <>
            <ol>
              <li>In My RSpace → My Profile, generate an API key.</li>
              <li>
                Use the API key in your scripts with the following example
                patterns:
              </li>
            </ol>
            <Typography variant="body2" sx={{ mt: 2, mb: 1 }}>
              <strong>cURL example:</strong>
            </Typography>
            <pre
              style={{
                background: "#f5f5f5",
                padding: "8px",
                overflowX: "auto",
              }}
            >
              {`curl -H "apiKey: YOUR_API_KEY" \\
     -H "Accept: application/json" \\
     https://your-rspace-instance.com/api/v1/userDetails/whoami`}
            </pre>
            <Typography variant="body2" sx={{ mt: 2, mb: 1 }}>
              <strong>Python SDK:</strong>
              <pre
                style={{
                  background: "#f5f5f5",
                  padding: "8px",
                  overflowX: "auto",
                }}
              >
                {`# Install with: pip install rspace-client
import os
from rspace_client.eln import eln

client = eln.ELNClient("https://your-rspace-instance.com", "YOUR_API_KEY")
print(client.get_status())`}
              </pre>
            </Typography>
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

export default React.memo(observer(ApiDirect));
