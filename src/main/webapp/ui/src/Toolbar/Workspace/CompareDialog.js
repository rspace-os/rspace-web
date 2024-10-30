//@flow

import React, { type Node } from "react";
import Portal from "@mui/material/Portal";
import Alerts from "../../components/Alerts/Alerts";
import { Dialog, DialogBoundary } from "../../components/DialogBoundary";
import ErrorBoundary from "../../components/ErrorBoundary";
import axios from "axios";
import useOauthToken from "../../common/useOauthToken";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import Grid from "@mui/material/Grid";
import Typography from "@mui/material/Typography";

function CompareDialog(): Node {
  const { getToken } = useOauthToken();
  const [documents, setDocuments] = React.useState<null | $ReadOnlyArray<{
    name: string,
    id: number,
    ...
  }>>(null);

  React.useEffect(() => {
    async function handler(
      event: Event & { detail: { ids: $ReadOnlyArray<string> } }
    ) {
      const token = await getToken();
      const docs = await Promise.all(
        event.detail.ids.map(async (id) => {
          const { data } = await axios.get<{ name: string, id: number, ... }>(
            `/api/v1/documents/${id}`,
            {
              headers: {
                Authorization: "Bearer " + token,
              },
            }
          );
          return data;
        })
      );
      setDocuments(docs);
    }
    window.addEventListener("OPEN_COMPARE_DIALOG", handler);
    return () => {
      window.removeEventListener("OPEN_COMPARE_DIALOG", handler);
    };
  }, []);

  if (!documents) return null;
  return (
    <Dialog
      open={true}
      onClose={() => {
        setDocuments(null);
      }}
    >
      <DialogTitle>Compare Documents</DialogTitle>
      <DialogContent>
        <Grid container direction="column" spacing={2}>
          <Grid item>
            <Typography variant="body2">
              Compare the selected documents and export a table with all of the
              fields to CSV.
            </Typography>
          </Grid>
          <Grid item>
            {documents.map(({ name, id }) => (
              <Typography key={id} variant="body1">
                {name}
              </Typography>
            ))}
          </Grid>
        </Grid>
      </DialogContent>
    </Dialog>
  );
}

export default function Wrapper(): Node {
  return (
    <ErrorBoundary topOfViewport>
      <Portal>
        <Alerts>
          <DialogBoundary>
            <CompareDialog />
          </DialogBoundary>
        </Alerts>
      </Portal>
    </ErrorBoundary>
  );
}
