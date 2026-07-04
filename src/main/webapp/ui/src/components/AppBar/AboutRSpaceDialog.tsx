import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Link from "@mui/material/Link";
import Stack from "@mui/material/Stack";
import { ThemeProvider } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import React from "react";
import { useTranslation } from "react-i18next";
import { helpDocsArticleUrl } from "@/modules/common/i18n/TransRichText";
import { useApplicationVersionQuery } from "@/modules/common/queries/applicationVersion";
import createAccentedTheme from "../../accentedTheme";
import RSpaceLogo from "../../assets/branding/rspace/logo.svg";
import { ACCENT_COLOR } from "../../assets/branding/rspace/other";
import { useDeploymentProperty } from "../../hooks/api/useDeploymentProperty";
import * as FetchingData from "../../util/fetchingData";
import { Dialog } from "../DialogBoundary";
import ErrorBoundary from "../ErrorBoundary";

const SUPPORT_EMAIL = "support@researchspace.com";

interface AboutRSpaceDialogProps {
  open: boolean;
  onClose: () => void;
}

/**
 * Renders the running application version. Suspends while it loads, so it must
 * be wrapped in a `<Suspense>` boundary, and throws on failure, so it must be
 * wrapped in an error boundary.
 */
function ApplicationVersion(): React.ReactElement {
  const { data: version } = useApplicationVersionQuery();
  return (
    <Typography variant="h6" gutterBottom>
      {version}
    </Typography>
  );
}

export function AboutRSpaceContent(): React.ReactElement {
  const { t } = useTranslation(["about", "common"]);
  const deploymentDescription = useDeploymentProperty("deployment.description");
  const helpEmail = useDeploymentProperty("deployment.helpEmail");

  return (
    <Stack sx={{ py: 2, alignItems: "center" }}>
      <Box
        sx={{
          width: 80,
          height: 80,
          mb: 2,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <img src={RSpaceLogo} alt={t("logo.alt")} />{" "}
      </Box>

      <Box sx={{ mb: 3 }}>
        <ErrorBoundary message={t("version.unavailable")}>
          <React.Suspense
            fallback={
              <Typography variant="h6" gutterBottom color="textSecondary">
                {t("version.loading")}
              </Typography>
            }
          >
            <ApplicationVersion />
          </React.Suspense>
        </ErrorBoundary>
      </Box>

      {FetchingData.match(deploymentDescription, {
        loading: () => null,
        error: () => null,
        success: (description) => {
          if (typeof description === "string" && description.trim()) {
            return (
              <Typography variant="body2" align="center" color="textSecondary" gutterBottom>
                {description}
              </Typography>
            );
          }
          return null;
        },
      })}

      <Typography variant="body2" align="center" color="textSecondary" gutterBottom>
        {t("support.generalLabel")} <Link href={`mailto:${SUPPORT_EMAIL}`}>{SUPPORT_EMAIL}</Link>
      </Typography>

      {FetchingData.match(helpEmail, {
        loading: () => null,
        error: () => null,
        success: (email) => {
          if (typeof email === "string" && email.trim()) {
            return (
              <Typography variant="body2" align="center" color="textSecondary" gutterBottom>
                {t("support.accountLabel")} <Link href={`mailto:${email}`}>{email}</Link>
              </Typography>
            );
          }
          return null;
        },
      })}

      <Typography variant="body2" align="center" gutterBottom sx={{ mt: 3 }}>
        {t("license")}
      </Typography>
      <Typography variant="caption" align="center" color="textSecondary">
        {t("copyright")}
      </Typography>

      <Box sx={{ mt: 2, mb: 3 }}>
        <Typography variant="body2" component="div">
          <Stack spacing={2} direction="row" sx={{ alignItems: "center" }}>
            <Link href="https://researchspace.com" target="_blank" rel="noreferrer">
              {t("links.website")}
            </Link>
            <Link href={helpDocsArticleUrl("mx11qvqg0i-changelog")} target="_blank" rel="noreferrer">
              {t("links.changelog")}
            </Link>
            <Link href="https://github.com/rspace-os" target="_blank" rel="noreferrer">
              {t("links.sourceCode")}
            </Link>
          </Stack>
        </Typography>
      </Box>
    </Stack>
  );
}

export default function AboutRSpaceDialog({ open, onClose }: AboutRSpaceDialogProps): React.ReactElement {
  const { t } = useTranslation(["about", "common"]);
  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
        <DialogTitle>{t("title")}</DialogTitle>
        <DialogContent>
          <AboutRSpaceContent />
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>{t("common:actions.close")}</Button>
        </DialogActions>
      </Dialog>
    </ThemeProvider>
  );
}
