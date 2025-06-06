import React from "react";
import { observer } from "mobx-react-lite";
import Alert from "@mui/material/Alert";
import GlobalId from "../../../components/GlobalId";
import AlertTitle from "@mui/material/AlertTitle";
import Button from "@mui/material/Button";
import Box from "@mui/material/Box";
import { type Template } from "../../../stores/definitions/Template";

type VersionInfoArgs = {
  template: Template;
  onUpdate?: () => void;
  disabled?: boolean;
};

function VersionInfo({
  template,
  onUpdate,
  disabled,
}: VersionInfoArgs): React.ReactNode {
  React.useEffect(() => {
    template.getLatest();
  }, []);

  return template.historicalVersion ? (
    <Alert
      severity="info"
      action={
        onUpdate && (
          <Box mr={2}>
            <Button
              color="inherit"
              size="small"
              variant="outlined"
              onClick={onUpdate}
              disabled={disabled}
            >
              Update
            </Button>
          </Box>
        )
      }
    >
      <AlertTitle>
        This is version {template.version} of the template.
      </AlertTitle>
      {template.latest && (
        <span>
          Latest version: <GlobalId record={template.latest} />
        </span>
      )}
    </Alert>
  ) : null;
}

export default observer(VersionInfo);
