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

interface AboutRSpaceDialogProps {
  open: boolean;
  onClose: () => void;
}

export default function AboutRSpaceDialog({
  open,
  onClose,
}: AboutRSpaceDialogProps): React.ReactElement {
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

          <Box mt={2} mb={3} display="flex" justifyContent="center" gap={2}>
            <Link
              href="https://researchspace.com"
              target="_blank"
              rel="noopener noreferrer"
              underline="hover"
              sx={{
                px: 2,
                py: 1,
                border: 1,
                borderColor: "divider",
                borderRadius: 1,
                textDecoration: "none",
                "&:hover": {
                  textDecoration: "none",
                  bgcolor: "action.hover",
                },
              }}
            >
              Website
            </Link>
            <Link
              href={docLinks.changelog}
              target="_blank"
              rel="noopener noreferrer"
              underline="hover"
              sx={{
                px: 2,
                py: 1,
                border: 1,
                borderColor: "divider",
                borderRadius: 1,
                textDecoration: "none",
                "&:hover": {
                  textDecoration: "none",
                  bgcolor: "action.hover",
                },
              }}
            >
              Changelog
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
