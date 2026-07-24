import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog, { dialogClasses } from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Grid from "@mui/material/Grid";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import type React from "react";
import { lazy, Suspense } from "react";
import { useTranslation } from "react-i18next";
import { helpDocsArticleUrl } from "@/modules/common/i18n/TransRichText";
import AlwaysNewWindowNavigationContext from "../../../../components/AlwaysNewWindowNavigationContext";
import HelpLinkIcon from "../../../../components/HelpLinkIcon";
import type { Identifier } from "../../../../stores/definitions/Identifier";
import type { InventoryRecord } from "../../../../stores/definitions/InventoryRecord";
import useStores from "../../../../stores/use-stores";
import PublishButton from "./PublishButton";

const IdentifierPublicPage = lazy(() => import("../../../../components/PublicPages/IdentifierPublicPage"));
const IdentifierDataGrid = lazy(() =>
  import("../../../../components/PublicPages/IdentifierPublicPage").then(({ IdentifierDataGrid: DataGrid }) => ({
    default: DataGrid,
  })),
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
const PublicPreviewDialog = ({ open, onClose, id, record }: PreviewDialogArgs): React.ReactNode => {
  const { t } = useTranslation(["inventory", "common"]);
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
              {t("fields.identifiers.publicPreviewDialog.title")}
            </Typography>
          </Grid>
          <Grid>
            <HelpLinkIcon
              link={helpDocsArticleUrl(
                record.recordType === "instrument" || record.recordType === "instrumentTemplate"
                  ? "pidinstIdentifiers"
                  : "igsnIdentifiers",
              )}
              title={t("fields.identifiers.publicPreviewDialog.helpTitle")}
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
            <Alert severity="warning">{t("fields.identifiers.publicPreviewDialog.missingDetails")}</Alert>
          </Box>
        )}
        <Button onClick={onClose}>{t("common:actions.close")}</Button>
        <PublishButton identifier={id} />
      </DialogActions>
    </Dialog>
  );
};

export default observer(PublicPreviewDialog);
