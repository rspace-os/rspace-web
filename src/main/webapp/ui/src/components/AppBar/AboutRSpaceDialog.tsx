import React from "react";
import { Dialog } from "../DialogBoundary";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import Link from "@mui/material/Link";
import docLinks from "../../assets/DocLinks";
import { useDeploymentProperty } from "../../eln/useDeploymentProperty";
import * as FetchingData from "../../util/fetchingData";
import useApplicationVersion from "../../api/useApplicationVersion";
import RSpaceLogo from "../../assets/branding/rspace/logo.svg";
import Stack from "@mui/material/Stack";
import { ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "../../accentedTheme";
import { ACCENT_COLOR } from "../../assets/branding/rspace/other";

interface AboutRSpaceDialogProps {
  open: boolean;
  onClose: () => void;
}

export function AboutRSpaceContent(): React.ReactElement {
  const deploymentDescription = useDeploymentProperty("deployment.description");
  const helpEmail = useDeploymentProperty("deployment.helpEmail");
  const version = useApplicationVersion();

  return (
    <Box display="flex" flexDirection="column" alignItems="center" py={2}>
      <Box
        width={80}
        height={80}
        mb={2}
        display="flex"
        alignItems="center"
        justifyContent="center"
      >
        <img src={RSpaceLogo} alt="RSpace Logo" />{" "}
      </Box>

      <Box sx={{ mb: 3 }}>
        {FetchingData.match(version, {
          loading: () => (
            <Typography variant="h6" gutterBottom color="textSecondary">
              Loading version...
            </Typography>
          ),
          error: () => (
            <Typography variant="h6" gutterBottom color="error">
              Version unavailable
            </Typography>
          ),
          success: (versionString) => (
            <Typography variant="h6" gutterBottom>
              {versionString}
            </Typography>
          ),
        })}
      </Box>

      {FetchingData.match(deploymentDescription, {
        loading: () => null,
        error: () => null,
        success: (description) => {
          if (typeof description === "string" && description.trim()) {
            return (
              <Typography
                variant="body2"
                align="center"
                color="textSecondary"
                gutterBottom
              >
                {description}
              </Typography>
            );
          }
          return null;
        },
      })}

      <Typography
        variant="body2"
        align="center"
        color="textSecondary"
        gutterBottom
      >
        For general support, email:{" "}
        <Link href="mailto:support@researchspace.com">
          support@researchspace.com
        </Link>
      </Typography>

      {FetchingData.match(helpEmail, {
        loading: () => null,
        error: () => null,
        success: (email) => {
          if (typeof email === "string" && email.trim()) {
            return (
              <Typography
                variant="body2"
                align="center"
                color="textSecondary"
                gutterBottom
              >
                For account and group queries, email:{" "}
                <Link href={`mailto:${email}`}>{email}</Link>
              </Typography>
            );
          }
          return null;
        },
      })}

      <Typography variant="body2" align="center" gutterBottom sx={{ mt: 3 }}>
        RSpace is open-source, and powered by open-source libraries.
        <br />
        RSpace is licensed under AGPL.
      </Typography>
      <Typography variant="caption" align="center" color="textSecondary">
        © 2025 ResearchSpace
      </Typography>

      <Box mt={2} mb={3}>
        <Typography variant="body2">
          <Stack spacing={2} direction="row" alignItems="center">
            <Link
              href="https://researchspace.com"
              target="_blank"
              rel="noreferrer"
            >
              Website
            </Link>
            <Link href={docLinks.changelog} target="_blank" rel="noreferrer">
              Changelog
            </Link>
            <Link
              href="https://github.com/rspace-os"
              target="_blank"
              rel="noreferrer"
            >
              Source Code
            </Link>
          </Stack>
        </Typography>
      </Box>
    </Box>
  );
}

export default function AboutRSpaceDialog({
  open,
  onClose,
}: AboutRSpaceDialogProps): React.ReactElement {
  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
        <DialogTitle>About RSpace</DialogTitle>
        <DialogContent>
          <AboutRSpaceContent />
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Close</Button>
        </DialogActions>
      </Dialog>
    </ThemeProvider>
  );
}
