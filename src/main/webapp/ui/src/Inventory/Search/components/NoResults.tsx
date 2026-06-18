import Box from "@mui/material/Box";
import Divider from "@mui/material/Divider";
import Link from "@mui/material/Link";
import { darken, useTheme } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import type React from "react";
import NoResultsSvg from "@/assets/graphics/NoResults.svg";
import docLinks from "../../../assets/DocLinks";

type NoResultsArgs = {
  query: string | null;
};

function NoResults({ query }: NoResultsArgs): React.ReactNode {
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
        No results.
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
        Try searching for a different term, or use the advanced search to change search filters.
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
            For more information on using Lucene queries, see{" "}
            <Link href={docLinks.luceneSyntax} rel="noreferrer" target="_blank">
              advanced search
            </Link>{" "}
            and the related{" "}
            <Link href="https://lucene.apache.org/core/2_9_4/queryparsersyntax.html" rel="noreferrer" target="_blank">
              Apache page
            </Link>
            .
          </Typography>
        </>
      )}
    </Box>
  );
}

export default NoResults;
