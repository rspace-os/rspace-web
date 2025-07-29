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

interface AboutRSpaceDialogProps {
  open: boolean;
  onClose: () => void;
}

export default function AboutRSpaceDialog({
  open,
  onClose,
}: AboutRSpaceDialogProps): React.ReactElement {
  const deploymentDescription = useDeploymentProperty("deployment.description");
  const helpEmail = useDeploymentProperty("deployment.helpEmail");

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>About RSpace</DialogTitle>
      <DialogContent>
        <Box display="flex" flexDirection="column" alignItems="center" py={2}>
          {/* TODO: Add RSpace logo image */}
          <Box
            width={80}
            height={80}
            mb={2}
            display="flex"
            alignItems="center"
            justifyContent="center"
            bgcolor="grey.200"
            borderRadius={1}
          >
            <Typography variant="caption" color="textSecondary">
              LOGO
            </Typography>
          </Box>

          <Typography variant="h6" gutterBottom>
            Version 1.111.1
          </Typography>

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

          <Box mt={2} mb={3} display="flex" justifyContent="center" gap={2}>
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
          </Box>

          <Typography variant="body2" align="center" gutterBottom>
            RSpace is licensed under the AGPL [legal text goes here]
          </Typography>
          <Typography variant="caption" align="center" color="textSecondary">
            © 2025 ResearchSpace [All Rights Reserved?]
          </Typography>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
