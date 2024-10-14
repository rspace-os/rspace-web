//@flow strict

import Grid from "@mui/material/Grid";
import React, { type Node, type AbstractComponent } from "react";
import IntegrationCard from "../IntegrationCard";
import AscensciaIcon from "../icons/Ascenscia.svg";
import { observer } from "mobx-react-lite";

export const COLOR = {
  hue: 243,
  saturation: 99,
  lightness: 68,
};

/*
 * The integration is actually on Ascenscia's end; the user passes their RSpace
 * API key to Ascenscia.
 */
function Ascenscia(): Node {
  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="Ascenscia"
        explanatoryText="A highly specialized voice assistant mobile application for scientific labs."
        image={AscensciaIcon}
        color={COLOR}
        usageText="The software solution acts as a personal assistant for scientists in the labs to mediate their interactions with Electronic Lab Notebook outside of the lab."
        helpLinkText="Ascenscia integration docs"
        website="ascenscia.ai"
        docLink="ascenscia"
        setupSection={
          <ol>
            <li>In MyRSpace, generate an API key.</li>
            <li>Enter your API key on Ascenscia&apos;s &quot;RSpace login&quot;page".</li>
            <li>Start using Ascenscia.</li>
          </ol>
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

export default (React.memo(observer(Ascenscia)): AbstractComponent<{||}>);
