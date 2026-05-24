import React from "react";
import Grid from "@mui/material/Grid";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import Link from "@mui/material/Link";
import HeroImage from "/src/assets/graphics/NoActiveResult.svg";
import docLinks from "../../../assets/DocLinks";
import { darken } from "@mui/material/styles";

const Title = () => (
  <Typography variant="h1">Welcome to RSpace Inventory!</Typography>
);

const Subtitle = () => (
  <Typography
    variant="subtitle1"
    sx={{
      color: (theme) => darken(theme.palette.primary.main, 0.2),
    }}
  >
    Let&apos;s get you started with the system.
  </Typography>
);

const GetStartedButton = () => (
  <Link
    href={docLinks.gettingStarted}
    target="_blank"
    rel="noreferrer"
    underline="always"
    style={{
      fontSize: "1.4em",
      textUnderlineOffset: "3px",
    }}
  >
    Get Started Guide
  </Link>
);

export default function NoActiveResultPlaceholder(): React.ReactNode {
  return (
    <>
      <Grid
        container
        sx={{ justifyContent: "space-around", minHeight: "100vh" }}
      >
        <Grid sx={{ alignSelf: "center" }}>
          <Grid container sx={{ alignItems: "center", flexDirection: "column" }}>
            <Grid>
              <Box sx={{ mb: 1, mt: 2 }}>
                <Title />
              </Box>
            </Grid>
            <Grid>
              <Box sx={{ mb: 3 }}>
                <Subtitle />
              </Box>
            </Grid>
            <Grid>
              <Box sx={{ mb: 2 }}>
                <GetStartedButton />
              </Box>
            </Grid>
            <Grid>
              <img src={HeroImage} />
            </Grid>
          </Grid>
        </Grid>
      </Grid>
    </>
  );
}
