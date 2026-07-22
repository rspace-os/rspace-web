import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import FormGroup from "@mui/material/FormGroup";
import FormLabel from "@mui/material/FormLabel";
import Grid from "@mui/material/Grid";
import Skeleton from "@mui/material/Skeleton";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import ApiService from "../../../common/InvApiService";
import GlobalIdLink from "../../../components/GlobalId";
import NoValue from "../../../components/NoValue";
import UserDetails from "../../../components/UserDetails";
import TransRichText from "../../../modules/common/i18n/TransRichText";
import type { GlobalId } from "../../../stores/definitions/BaseRecord";
import type { Document, DocumentAttrs } from "../../../stores/definitions/Document";
import type { Factory } from "../../../stores/definitions/Factory";
import RsSet, { unionWith } from "../../../util/set";

type ReferencingItem = {
  sourceGlobalId: string;
  sourceName: string;
  sourceType: string;
  relationType: string;
  versionPin: number | null;
};

type State =
  | { state: "init" }
  | { state: "loading" }
  | {
      state: "success";
      documents: RsSet<Document>;
      referencingItems: ReferencingItem[];
    }
  | { state: "fail"; error: Error };

import { GLOBAL_ID_PATTERN, INVENTORY_PREFIX_TO_API_PATH } from "@/Inventory/components/Fields/Link/linkTarget";

function referencingItemsEndpoint(globalId: string): string | null {
  const match = GLOBAL_ID_PATTERN.exec(globalId);
  if (!match) return null;
  // sample and instrument templates have no typed referencingItems endpoint;
  // use the generic by-Global-ID route instead
  if (match[1] === "IT" || match[1] === "NT") return `referencingItems/${globalId}`;
  const segment = INVENTORY_PREFIX_TO_API_PATH[match[1]];
  if (segment) return `${segment}/${match[2]}/referencingItems`;
  return null;
}

function ReferencingItemsTable({ items }: { items: ReferencingItem[] }): React.ReactElement {
  const { t } = useTranslation("inventory");
  return (
    <Table size="small" aria-label={t("moreInfo.linkedDocuments.inventoryLinksTable")}>
      <TableHead>
        <TableRow>
          <TableCell>{t("moreInfo.linkedDocuments.columns.name")}</TableCell>
          <TableCell>{t("moreInfo.globalId")}</TableCell>
          <TableCell>{t("moreInfo.linkedDocuments.columns.relation")}</TableCell>
          {/* Which version of THIS item the linking item points at (not a version
              of the linking item itself). */}
          <TableCell>{t("moreInfo.linkedDocuments.columns.linkedVersion")}</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {items.map((item, index) => (
          // one item can link to the same target through several fields, so
          // the global id alone is not a unique row key
          <TableRow key={`${item.sourceGlobalId}-${index}`}>
            <TableCell>{item.sourceName}</TableCell>
            <TableCell>
              <a href={`/globalId/${item.sourceGlobalId}`} rel="noreferrer" target="_blank">
                {item.sourceGlobalId}
              </a>
            </TableCell>
            <TableCell>{item.relationType}</TableCell>
            <TableCell>
              {item.versionPin != null ? `v${item.versionPin}` : t("moreInfo.linkedDocuments.latest")}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function DocumentsTable({ documents }: { documents: RsSet<Document> }): React.ReactElement {
  const { t } = useTranslation("inventory");
  return (
    <Table aria-label={t("moreInfo.linkedDocuments.documentsTable")}>
      <TableHead>
        <TableRow>
          <TableCell>{t("moreInfo.linkedDocuments.columns.name")}</TableCell>
          <TableCell>{t("moreInfo.globalId")}</TableCell>
          <TableCell>{t("moreInfo.linkedDocuments.columns.owner")}</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {documents.map((document) => (
          <TableRow key={document.globalId}>
            <TableCell>{document.name}</TableCell>
            <TableCell>
              <GlobalIdLink record={document} />
            </TableCell>
            <TableCell>
              {document.owner ? (
                <UserDetails
                  userId={document.owner.id}
                  fullName={document.owner.fullName}
                  position={["bottom", "right"]}
                />
              ) : (
                "-"
              )}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function DialogContents({ state }: { state: State }): React.ReactNode {
  const { t } = useTranslation("inventory");
  if (state.state === "loading") return <Skeleton variant="rectangular" width={210} height={118} />;
  if (state.state === "fail") return <Alert severity="error">{state.error.message}</Alert>;
  if (state.state === "success") {
    const { documents, referencingItems } = state;
    const bothEmpty = documents.size === 0 && referencingItems.length === 0;
    return (
      <>
        <Box>
          <Typography variant="subtitle1" gutterBottom>
            {t("moreInfo.linkedDocuments.documentsTable")}
          </Typography>
          {documents.size > 0 ? (
            <DocumentsTable documents={documents} />
          ) : (
            <NoValue label={t("moreInfo.linkedDocuments.noDocuments")} />
          )}
        </Box>
        <Box sx={{ mt: 2 }}>
          <Typography variant="subtitle1" gutterBottom>
            {t("moreInfo.linkedDocuments.inventoryLinksTable")}
          </Typography>
          {referencingItems.length > 0 ? (
            <ReferencingItemsTable items={referencingItems} />
          ) : (
            <NoValue label={t("moreInfo.linkedDocuments.noInventoryLinks")} />
          )}
        </Box>
        {bothEmpty && (
          <>
            <Box sx={{ mt: 1 }}>
              <Typography variant="body1">
                <TransRichText i18nKey="inventory:moreInfo.linkedDocumentsHelp.listOfMaterials" />
              </Typography>
            </Box>
            <Box sx={{ mt: 1 }}>
              <Typography variant="body1">{t("moreInfo.linkedDocumentsHelp.linkField")}</Typography>
            </Box>
          </>
        )}
      </>
    );
  }
  return null;
}

type LinkedDocumentsArgs = {
  globalId: GlobalId;
  factory: Factory | null;
};

function LinkedDocuments({ globalId, factory }: LinkedDocumentsArgs): React.ReactNode {
  const { t } = useTranslation(["inventory", "common"]);
  const [open, setOpen] = useState(false);
  const [state, setState] = useState<State>({ state: "init" });

  useEffect(() => {
    if (open) {
      if (!factory) throw new Error("Factory is required");
      setState({ state: "loading" });
      void (async () => {
        try {
          const docsRequest = ApiService.get<Array<{ elnDocument: DocumentAttrs }>>(
            `listOfMaterials/forInventoryItem/${globalId}`,
          );
          const refsEndpoint = referencingItemsEndpoint(globalId);
          const refsRequest = refsEndpoint
            ? ApiService.get<{ referencingItems: ReferencingItem[] }>(refsEndpoint)
            : Promise.resolve({
                data: { referencingItems: [] },
              } as { data: { referencingItems: ReferencingItem[] } });

          const [docsResponse, refsResponse] = await Promise.all([docsRequest, refsRequest]);

          // always use a new factory so that closing and reopening the
          // dialog uses the newly fetched data
          const newFactory = factory.newFactory();

          setState({
            state: "success",
            documents: unionWith(
              ({ id }: Document) => id,
              docsResponse.data.map(
                ({ elnDocument: { globalId: docGlobalId, name, id, owner } }: { elnDocument: DocumentAttrs }) =>
                  new RsSet([
                    newFactory.newDocument({
                      globalId: docGlobalId,
                      name,
                      id,
                      owner,
                    }),
                  ]),
              ),
            ),
            referencingItems: refsResponse.data.referencingItems ?? [],
          });
        } catch (e) {
          setState({ state: "fail", error: e as Error });
        }
      })();
    }
  }, [open]);

  return (
    <Grid>
      <FormControl component="fieldset" style={{ alignItems: "flex-start" }}>
        <FormLabel component="legend">{t("moreInfo.linkedDocuments.title")}</FormLabel>
        <FormGroup>
          <Button
            variant="outlined"
            disableElevation
            onClick={() => {
              setOpen(true);
            }}
            disabled={!factory}
          >
            {t("moreInfo.linkedDocuments.show")}
          </Button>
          <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="sm">
            <DialogTitle>{t("moreInfo.linkedDocuments.title")}</DialogTitle>
            <DialogContent>
              <DialogContents state={state} />
            </DialogContent>
            <DialogActions>
              <Button onClick={() => setOpen(false)}>{t("common:actions.close")}</Button>
            </DialogActions>
          </Dialog>
        </FormGroup>
      </FormControl>
    </Grid>
  );
}

export default observer(LinkedDocuments);
