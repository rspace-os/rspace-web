import Grid from "@mui/material/Grid";
import React from "react";
import IntegrationCard from "../IntegrationCard";
import JoveIcon from "../../../assets/branding/jove/logo.svg";
import { LOGO_COLOR } from "../../../assets/branding/jove";
import Typography from "@mui/material/Typography";
import Stack from "@mui/material/Stack";

function Jove(): React.ReactNode {
  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="JoVE"
        integrationState={{
          mode: "EXTERNAL",
          credentials: null,
        }}
        explanatoryText="Embed JoVE and other video players (e.g., YouTube, TIB AV-Portal)."
        image={JoveIcon}
        color={LOGO_COLOR}
        update={() => {}}
        usageText="Embed JoVE videos in RSpace documents by opening the Video tool from the Documents Editor, pasting a JoVE URL, and inserting the generated embed at the cursor position."
        helpLinkText="Video integration docs"
        website="jove.com"
        docLink="videoIntegration"
        setupSection={
          <Stack direction="column" gap={2}>
            <ol>
              <li>
                Open a document in the Documents Editor and click the{" "}
                <strong>Video</strong> button in the editor toolbar, the insert
                menu, or the slash menu.
              </li>
              <li>
                Paste a full JoVE URL from a supported <code>jove.com</code>{" "}
                page, for example <code>https://www.jove.com/v/60908/...</code>{" "}
                or <code>https://app.jove.com/v/60908/...</code>.
              </li>
              <li>
                Select <strong>Insert</strong> to place the JoVE video embed at
                the current cursor position.
              </li>
            </ol>
            <Typography variant="body2">
              <strong>Note:</strong> Pasting a JoVE URL directly into the
              document does not auto-embed it; use the Video editor action when
              you want an embed.
            </Typography>
          </Stack>
        }
      />
    </Grid>
  );
}

export default React.memo(Jove);
