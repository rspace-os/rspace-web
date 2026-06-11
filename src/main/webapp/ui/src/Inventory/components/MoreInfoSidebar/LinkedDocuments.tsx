import React, { useState, useEffect } from "react";
import { observer } from "mobx-react-lite";
import FormControl from "@mui/material/FormControl";
import FormGroup from "@mui/material/FormGroup";
import FormLabel from "@mui/material/FormLabel";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Table from "@mui/material/Table";
import TableRow from "@mui/material/TableRow";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableBody from "@mui/material/TableBody";
import ApiService from "../../../common/InvApiService";
import GlobalIdLink from "../../../components/GlobalId";
import { type Factory } from "../../../stores/definitions/Factory";
import RsSet, { unionWith } from "../../../util/set";
import Grid from "@mui/material/Grid";
import Skeleton from "@mui/material/Skeleton";
import NoValue from "../../../components/NoValue";
import {
  type Document,
  type DocumentAttrs,
} from "../../../stores/definitions/Document";
import Alert from "@mui/material/Alert";
import { type GlobalId } from "../../../stores/definitions/BaseRecord";
import UserDetails from "../../../components/UserDetails";
import Typography from "@mui/material/Typography";
import docLinks from "../../../assets/DocLinks";
import Box from "@mui/material/Box";

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

const GLOBAL_ID_PATTERN = /^([A-Z]{2})(\d+)(?:v\d+)?$/;
const PREFIX_TO_PATH: Record<string, string> = {
  SA: "samples",
  SS: "subSamples",
  IC: "containers",
  IN: "instruments",
};

function referencingItemsEndpoint(globalId: string): string | null {
  const match = GLOBAL_ID_PATTERN.exec(globalId);
  if (!match) return null;
  const segment = PREFIX_TO_PATH[match[1]];
  if (!segment) return null;
  return `${segment}/${match[2]}/referencingItems`;
}

function ReferencingItemsTable({
  items,
}: {
  items: ReferencingItem[];
}): React.ReactElement {
  return (
    <Table size="small" aria-label="Inventory items linking to this item">
      <TableHead>
        <TableRow>
          <TableCell>Name</TableCell>
          <TableCell>Global ID</TableCell>
          <TableCell>Relation</TableCell>
          {/* Which version of THIS item the linking item points at (not a version
              of the linking item itself). */}
          <TableCell>Linked version</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {items.map((item) => (
          <TableRow key={item.sourceGlobalId}>
            <TableCell>{item.sourceName}</TableCell>
            <TableCell>
              <a
                href={`/globalId/${item.sourceGlobalId}`}
                rel="noreferrer"
                target="_blank"
              >
                {item.sourceGlobalId}
              </a>
            </TableCell>
            <TableCell>{item.relationType}</TableCell>
            <TableCell>
              {item.versionPin != null ? `v${item.versionPin}` : "Latest"}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function DocumentsTable({
  documents,
}: {
  documents: RsSet<Document>;
}): React.ReactElement {
  return (
    <Table aria-label="Documents containing this item">
      <TableHead>
        <TableRow>
          <TableCell>Name</TableCell>
          <TableCell>Global ID</TableCell>
          <TableCell>Owner</TableCell>
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
                <>-</>
              )}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function DialogContents({ state }: { state: State }): React.ReactNode {
  if (state.state === "loading")
    return <Skeleton variant="rectangular" width={210} height={118} />;
  if (state.state === "fail")
    return <Alert severity="error">{state.error.message}</Alert>;
  if (state.state === "success") {
    const { documents, referencingItems } = state;
    const bothEmpty = documents.size === 0 && referencingItems.length === 0;
    return (
      <>
        <Box>
          <Typography variant="subtitle1" gutterBottom>
            Documents containing this item
          </Typography>
          {documents.size > 0 ? (
            <DocumentsTable documents={documents} />
          ) : (
            <NoValue label="No documents" />
          )}
        </Box>
        <Box sx={{ mt: 2 }}>
          <Typography variant="subtitle1" gutterBottom>
            Inventory items linking to this item
          </Typography>
          {referencingItems.length > 0 ? (
            <ReferencingItemsTable items={referencingItems} />
          ) : (
            <NoValue label="No inventory links" />
          )}
        </Box>
        {bothEmpty && (
          <>
            <Box sx={{ mt: 1 }}>
              <Typography variant="body1">
                Adding this item to a document&apos;s{" "}
                <a
                  href={docLinks.listOfMaterials}
                  rel="noreferrer"
                  target="_blank"
                >
                  List of Materials
                </a>{" "}
                will add an entry for the document in this panel.
              </Typography>
            </Box>
            <Box sx={{ mt: 1 }}>
              <Typography variant="body1">
                Other Inventory items that link to this item through a Link
                custom field will also be listed here.
              </Typography>
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

function LinkedDocuments({
  globalId,
  factory,
}: LinkedDocumentsArgs): React.ReactNode {
  const [open, setOpen] = useState(false);
  const [state, setState] = useState<State>({ state: "init" });

  useEffect(() => {
    if (open) {
      if (!factory) throw new Error("Factory is required");
      setState({ state: "loading" });
      void (async () => {
        try {
          const docsRequest = ApiService.get<
            Array<{ elnDocument: DocumentAttrs }>
          >(`listOfMaterials/forInventoryItem/${globalId}`);
          const refsEndpoint = referencingItemsEndpoint(globalId);
          const refsRequest = refsEndpoint
            ? ApiService.get<{ referencingItems: ReferencingItem[] }>(
                refsEndpoint,
              )
            : Promise.resolve({
                data: { referencingItems: [] },
              } as { data: { referencingItems: ReferencingItem[] } });

          const [docsResponse, refsResponse] = await Promise.all([
            docsRequest,
            refsRequest,
          ]);

          // always use a new factory so that closing and reopening the
          // dialog uses the newly fetched data
          const newFactory = factory.newFactory();

          setState({
            state: "success",
            documents: unionWith(
              ({ id }: Document) => id,
              docsResponse.data.map(
                ({
                  elnDocument: { globalId: docGlobalId, name, id, owner },
                }: {
                  elnDocument: DocumentAttrs;
                }) =>
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
        <FormLabel component="legend">Linked Documents</FormLabel>
        <FormGroup>
          <Button
            variant="outlined"
            disableElevation
            onClick={() => {
              setOpen(true);
            }}
            disabled={!factory}
          >
            Show Linked Documents
          </Button>
          <Dialog
            open={open}
            onClose={() => setOpen(false)}
            fullWidth
            maxWidth="sm"
          >
            <DialogTitle>Linked Documents</DialogTitle>
            <DialogContent>
              <DialogContents state={state} />
            </DialogContent>
            <DialogActions>
              <Button onClick={() => setOpen(false)}>Close</Button>
            </DialogActions>
          </Dialog>
        </FormGroup>
      </FormControl>
    </Grid>
  );
}

export default observer(LinkedDocuments);
