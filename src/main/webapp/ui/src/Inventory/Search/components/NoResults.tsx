import Box from "@mui/material/Box";
import Divider from "@mui/material/Divider";
import Link from "@mui/material/Link";
import { darken, useTheme } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useTranslation } from "react-i18next";
import NoResultsSvg from "@/assets/graphics/NoResults.svg";
import TransRichText from "@/modules/common/i18n/TransRichText";
import docLinks from "../../../assets/DocLinks";

type NoResultsArgs = {
  query: string | null;
};

function NoResults({ query }: NoResultsArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const theme = useTheme();
  return (
    <Box
      sx={{
        fontWeight: 700,
        fontSize: "1.6rem",
        display: "flex",
        flexDirection: "column",
        height: "100%",
        alignItems: "center",
        mb: 1,
      }}
    >
      <Box
        sx={{
          flexGrow: 1,
          background: `center / contain no-repeat url("${NoResultsSvg}")`,
          width: "100%",
          minHeight: "50px",
          maxHeight: "200px",
        }}
      />
      <Typography variant="inherit" component="span" sx={{ color: darken(theme.palette.primary.main, 0.2) }}>
        {t("search.noResults.title")}
      </Typography>
      <Typography
        sx={{
          textAlign: "center",
          color: darken(theme.palette.primary.main, 0.2),
          mt: 2,
          fontSize: "1rem",
          maxWidth: "20em",
        }}
      >
        {t("search.noResults.tryDifferentSearch")}
      </Typography>
      {query !== "" && (
        <>
          <Divider orientation="horizontal" sx={{ width: "75%", mt: 2 }} />
          <Typography
            sx={{
              textAlign: "center",
              color: darken(theme.palette.primary.main, 0.2),
              mt: 2,
              fontSize: "1rem",
              maxWidth: "20em",
            }}
          >
            <TransRichText
              ns="inventory"
              i18nKey="search.noResults.luceneInfo"
              components={{
                luceneLink: <Link href={docLinks.luceneSyntax} rel="noreferrer" target="_blank" />,
                apacheLink: (
                  <Link
                    href="https://lucene.apache.org/core/2_9_4/queryparsersyntax.html"
                    rel="noreferrer"
                    target="_blank"
                  />
                ),
              }}
            />
          </Typography>
        </>
      )}
    </Box>
  );
}

export default NoResults;
