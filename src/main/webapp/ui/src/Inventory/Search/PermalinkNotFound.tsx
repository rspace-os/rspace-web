import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import Box from "@mui/material/Box";
import Link from "@mui/material/Link";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext } from "react";
import NavigateContext from "../../stores/contexts/Navigate";
import type { Permalink, PermalinkType } from "../../stores/definitions/Search";

const TYPE_LABELS: Record<PermalinkType, string> = {
  sample: "sample",
  subsample: "subsample",
  container: "container",
  sampletemplate: "sample template",
  instrument: "instrument",
  instrumenttemplate: "instrument template",
};

type PermalinkNotFoundArgs = {
  permalink: Permalink;
};

/**
 * Shown in the right panel when a permalink points at a record, or a version
 * of a record, that cannot be found. Versioned permalinks get a specific
 * message with a link to the latest state of the record.
 */
function PermalinkNotFound({ permalink }: PermalinkNotFoundArgs): React.ReactNode {
  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();
  const latestUrl = `/inventory/${permalink.type}/${permalink.id}`;

  return (
    <Box sx={{ p: 2 }}>
      {permalink.version != null ? (
        <Alert severity="warning">
          <AlertTitle>
            Version {permalink.version} of this {TYPE_LABELS[permalink.type]} could not be found.
          </AlertTitle>
          The version may never have existed.{" "}
          <Link
            href={latestUrl}
            onClick={(e: React.MouseEvent) => {
              e.preventDefault();
              navigate(latestUrl);
            }}
          >
            View the latest version
          </Link>
          .
        </Alert>
      ) : (
        <Alert severity="warning">
          <AlertTitle>This {TYPE_LABELS[permalink.type]} could not be found.</AlertTitle>
          It may have been deleted, or you may not have permission to view it.
        </Alert>
      )}
    </Box>
  );
}

export default observer(PermalinkNotFound);
