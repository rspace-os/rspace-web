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
import docLinks from "../../../assets/DocLinks";
import ApiService from "../../../common/InvApiService";
import GlobalIdLink from "../../../components/GlobalId";
import NoValue from "../../../components/NoValue";
import UserDetails from "../../../components/UserDetails";
// biome-ignore lint/style/useImportType: initial biome migration
import { type GlobalId } from "../../../stores/definitions/BaseRecord";
// biome-ignore lint/style/useImportType: initial biome migration
import { type Document, type DocumentAttrs } from "../../../stores/definitions/Document";
// biome-ignore lint/style/useImportType: initial biome migration
import { type Factory } from "../../../stores/definitions/Factory";
import RsSet, { unionWith } from "../../../util/set";

type State =
  | { state: "init" }
  | { state: "loading" }
  | { state: "success"; documents: RsSet<Document> }
  | { state: "fail"; error: Error };

type LinkedDocumentsArgs = {
  globalId: GlobalId;
  factory: Factory | null;
};

function LinkedDocuments({ globalId, factory }: LinkedDocumentsArgs): React.ReactNode {
  const [open, setOpen] = useState(false);
  const [state, setState] = useState<State>({ state: "init" });

  useEffect(() => {
    if (open) {
      if (!factory) throw new Error("Factory is required");
      setState({ state: "loading" });
      void (async () => {
        try {
          const { data } = await ApiService.get<Array<{ elnDocument: DocumentAttrs }>>(
            `listOfMaterials/forInventoryItem/${globalId}`,
          );

          // always use a new factory so that closing and reopening the
          // dialog uses the newly fetched data
          const newFactory = factory.newFactory();

          setState({
            state: "success",
            // take the union of the documents, where id defines equality, to
            // only show each document in the table once
            documents: unionWith(
              ({ id }: Document) => id,
              data.map(
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
          });
        } catch (e) {
          setState({ state: "fail", error: e as Error });
        }
      })();
    }
  }, [open]);

  return (
    <FormControl component="fieldset" sx={{ alignItems: "flex-start" }}>
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
        <Dialog open={open} onClose={() => setOpen(false)}>
          <DialogTitle>Linked Documents</DialogTitle>
          <DialogContent>
            {state.state === "loading" && <Skeleton variant="rectangular" width={210} height={118} />}
            {state.state === "fail" && <Alert severity="error">{state.error.message}</Alert>}
            {state.state === "success" && state.documents.size === 0 && (
              <>
                <NoValue label="None" />
                <Box sx={{ mt: 1 }}>
                  <Typography variant="body1">
                    Adding this item to a document&apos;s{" "}
                    <a href={docLinks.listOfMaterials} rel="noreferrer" target="_blank">
                      List of Materials
                    </a>{" "}
                    will add an entry for the document in this panel.
                  </Typography>
                </Box>
              </>
            )}
            {state.state === "success" && state.documents.size > 0 && (
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Name</TableCell>
                    <TableCell>Global ID</TableCell>
                    <TableCell>Owner</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {state.documents.map((document) => (
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
                          <>&emdash;</>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setOpen(false)}>Close</Button>
          </DialogActions>
        </Dialog>
      </FormGroup>
    </FormControl>
  );
}

export default observer(LinkedDocuments);
