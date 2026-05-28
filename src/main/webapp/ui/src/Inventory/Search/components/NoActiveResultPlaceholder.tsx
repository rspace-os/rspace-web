import React from "react";
import Stack from "@mui/material/Stack";
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
    sx={{
      fontSize: "1.4em",
      textUnderlineOffset: "3px",
    }}
  >
    Get Started Guide
  </Link>
);

export default function NoActiveResultPlaceholder(): React.ReactNode {
  return (
    <Stack
      direction="row"
      sx={{ justifyContent: "space-around", minHeight: "100vh" }}
    >
      <Stack sx={{ alignSelf: "center", alignItems: "center" }}>
        <Box sx={{ mb: 1, mt: 2 }}>
          <Title />
        </Box>
        <Box sx={{ mb: 3 }}>
          <Subtitle />
        </Box>
        <Box sx={{ mb: 2 }}>
          <GetStartedButton />
        </Box>
        <img src={HeroImage} />
      </Stack>
    </Stack>
  );
}
