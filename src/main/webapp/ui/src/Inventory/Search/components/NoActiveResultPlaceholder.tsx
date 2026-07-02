import Box from "@mui/material/Box";
import Link from "@mui/material/Link";
import Stack from "@mui/material/Stack";
import { darken } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useTranslation } from "react-i18next";
import HeroImage from "/src/assets/graphics/NoActiveResult.svg";
import docLinks from "../../../assets/DocLinks";

export default function NoActiveResultPlaceholder(): React.ReactNode {
  const { t } = useTranslation("inventory");
  return (
    <Stack direction="row" sx={{ justifyContent: "space-around", minHeight: "100vh" }}>
      <Stack sx={{ alignSelf: "center", alignItems: "center" }}>
        <Box sx={{ mb: 1, mt: 2 }}>
          <Typography variant="h1">{t("search.noActiveResult.title")}</Typography>
        </Box>
        <Box sx={{ mb: 3 }}>
          <Typography
            variant="subtitle1"
            sx={{
              color: (theme) => darken(theme.palette.primary.main, 0.2),
            }}
          >
            {t("search.noActiveResult.body")}
          </Typography>
        </Box>
        <Box sx={{ mb: 2 }}>
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
            {t("search.noActiveResult.guideLink")}
          </Link>
        </Box>
        {/** biome-ignore lint/a11y/useAltText: initial biome migration */}
        <img src={HeroImage} />
      </Stack>
    </Stack>
  );
}
