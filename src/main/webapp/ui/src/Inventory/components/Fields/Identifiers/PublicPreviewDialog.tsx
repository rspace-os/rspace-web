import React, { Suspense, lazy } from "react";
import { observer } from "mobx-react-lite";
import useStores from "../../../../stores/use-stores";
import docLinks from "../../../../assets/DocLinks";
import HelpLinkIcon from "../../../../components/HelpLinkIcon";
import Typography from "@mui/material/Typography";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import { dialogClasses } from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Grid from "@mui/material/Grid";
import { type Identifier } from "../../../../stores/definitions/Identifier";
import PublishButton from "./PublishButton";
import { type InventoryRecord } from "../../../../stores/definitions/InventoryRecord";
import AlwaysNewWindowNavigationContext from "../../../../components/AlwaysNewWindowNavigationContext";

const IdentifierPublicPage = lazy(
  () => import("../../../../components/PublicPages/IdentifierPublicPage")
);
const IdentifierDataGrid = lazy(() =>
  import("../../../../components/PublicPages/IdentifierPublicPage").then(
    ({ IdentifierDataGrid: DataGrid }) => ({ default: DataGrid })
  )
);

type PreviewDialogArgs = {
  open: boolean;
  onClose: () => void;
  id: Identifier;
  record: InventoryRecord;
};

type PreviewRecordWithOptionalFields = InventoryRecord & {
  fields?: Array<{
    name: string;
    type: string;
    id: number;
    content: string | null;
    selectedOptions: Array<string> | null;
  }>;
};

/**
 * Dialog to preview public page for an identifier
 */
const PublicPreviewDialog = ({
  open,
  onClose,
  id,
  record,
}: PreviewDialogArgs): React.ReactNode => {
  const { uiStore } = useStores();
  if (!id.rsPublicId) return null;
  const publicId: string = id.rsPublicId;
  const fields = (record as PreviewRecordWithOptionalFields).fields;

  return (
    <Dialog
      sx={{
        [`& .${dialogClasses.paper}`]: {
          height: "100%",
        },
      }}
      open={open}
      onClose={onClose}
      maxWidth="lg"
      fullScreen={uiStore.isVerySmall}
      data-testid="PublicPreviewDialog"
    >
      <DialogTitle>
        <Grid container direction="row">
          <Grid>
            <Typography component="h2" variant="h6">
              Review your page before publishing
            </Typography>
          </Grid>
          <Grid>
            <HelpLinkIcon
              link={docLinks.IGSNIdentifiers}
              title="Info on handling Identifiers."
            />
          </Grid>
        </Grid>
      </DialogTitle>
      <DialogContent>
        <Box sx={(theme) => ({ border: `1px dashed ${theme.palette.lightestGrey}` })}>
          {id.state === "findable" ? (
            <Suspense>
              {/*
               * If the identifier has been published then we fetch the data
               * from the public API, even though we have all the same data,
               * to ensure that what is shown is exactly what is shown on the
               * public page.
               */}
              <IdentifierPublicPage publicId={publicId} />
            </Suspense>
          ) : (
            <AlwaysNewWindowNavigationContext>
              <Suspense>
                {/*
                 * If the identifier has not yet been published then we generate
                 * the same UI, but using the data that we already have. By
                 * doing so, we show in the preview what would be shown on the
                 * public page were the user to tap the "Publish" button right now.
                 */}
                <IdentifierDataGrid
                  identifier={id}
                  record={{
                    description: record.description,
                    tags: record.tags,
                    fields,
                    extraFields: record.extraFields.map((eF) => ({
                      name: eF.name,
                      id: eF.id,
                      content: eF.content,
                    })),
                  }}
                />
              </Suspense>
            </AlwaysNewWindowNavigationContext>
          )}
        </Box>
      </DialogContent>
      <DialogActions>
        {!id.isValid && (
          <Box sx={{ flexGrow: 1 }}>
            <Alert severity="warning">
              Some required details are missing. To enable publishing, please
              fill them in.
            </Alert>
          </Box>
        )}
        <Button onClick={onClose}>Close</Button>
        <PublishButton identifier={id} />
      </DialogActions>
    </Dialog>
  );
};

export default observer(PublicPreviewDialog);
