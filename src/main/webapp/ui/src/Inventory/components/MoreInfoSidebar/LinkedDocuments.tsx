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
import UserDetails from "../UserDetails";
import Typography from "@mui/material/Typography";
import docLinks from "../../../assets/DocLinks";
import Box from "@mui/material/Box";

type State =
  | { state: "init" }
  | { state: "loading" }
  | { state: "success"; documents: RsSet<Document> }
  | { state: "fail"; error: Error };

function DialogContents({ state }: { state: State }): React.ReactNode {
  if (state.state === "loading")
    return <Skeleton variant="rectangular" width={210} height={118} />;
  if (state.state === "fail")
    return <Alert severity="error">{state.error.message}</Alert>;
  if (state.state === "success") {
    const { documents } = state;
    if (documents.size === 0)
      return (
        <>
          <NoValue label="None" />
          <Box mt={1}>
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
        </>
      );
    return (
      <Table>
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
                  <>&emdash;</>
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
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
          const { data } = await ApiService.get<
            Array<{ elnDocument: DocumentAttrs }>
          >(`listOfMaterials/forInventoryItem/${globalId}`);

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
                  ])
              )
            ),
          });
        } catch (e) {
          setState({ state: "fail", error: e as Error });
        }
      })();
    }
  }, [open]);

  return (
    <Grid item>
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
          <Dialog open={open} onClose={() => setOpen(false)}>
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
