import React from "react";
import Portal from "@mui/material/Portal";
import Typography from "@mui/material/Typography";
import ErrorBoundary from "../../components/ErrorBoundary";
import Alerts from "../../components/Alerts/Alerts";
import { Dialog, DialogBoundary } from "../../components/DialogBoundary";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Button from "@mui/material/Button";
import SubmitSpinnerButton from "../../components/SubmitSpinnerButton";
import TagListing from "../../components/Tags/TagListing";
import Grid from "@mui/material/Grid";
import AddTag from "../../components/Tags/AddTag";
import axios from "@/common/axios";
import { type Tag, areSameTag } from "../../stores/definitions/Tag";
import RsSet, { flattenWithIntersectionWithEq } from "../../util/set";
import {
  parseEncodedTags,
  encodeTags,
} from "../../components/Tags/ParseEncodedTagStrings";
import { doNotAwait } from "../../util/Util";
import * as ArrayUtils from "../../util/ArrayUtils";
import AlertContext, {
  mkAlert,
  type AlertDetails,
} from "../../stores/contexts/Alert";
import { Optional } from "../../util/optional";
import docLinks from "../../assets/DocLinks";
import Analytics from "../../components/Analytics";
import AnalyticsContext from "../../stores/contexts/Analytics";

export default function Wrapper(): React.ReactNode {
  return (
    <Analytics>
      <ErrorBoundary topOfViewport>
        <Portal>
          <Alerts>
            <DialogBoundary>
              <TagDialog />
            </DialogBoundary>
          </Alerts>
        </Portal>
      </ErrorBoundary>
    </Analytics>
  );
}

function TagDialog(): React.ReactNode {
  const { addAlert } = React.useContext(AlertContext);
  const { trackEvent } = React.useContext(AnalyticsContext);
  const [saving, setSaving] = React.useState(false);
  const [enforcedOntologies, setEnforcedOntologies] = React.useState<
    null | boolean
  >(null);

  /*
   * the IDs of the selected records; documents, notebooks, folders, etc.
   * will be null before the user has opened the dialog
   */
  const [selectedIds, setSelectedIds] = React.useState<Array<number> | null>(
    null,
  );

  // a mapping of record ID to tags, as is currently saved on the server
  const [savedTagsMap, setSavedTagsMap] = React.useState<
    Record<number, Array<Tag>>
  >({});

  // the set of tags that all of the records have in common
  const [commonTags, setCommonTags] = React.useState<RsSet<Tag>>(new RsSet());

  // tags that the user has selected for adding to each of the records
  const [addedTags, setAddedTags] = React.useState<Array<Tag>>([]);

  // tags that the user has selected for removing from each of the records
  const [deletedTags, setDeletedTags] = React.useState<Array<Tag>>([]);

  const visibleTags = React.useMemo(() => {
    return [
      ...new RsSet(addedTags)
        .unionWithEq(commonTags, areSameTag)
        .subtractWithEq(new RsSet(deletedTags), areSameTag),
    ];
  }, [commonTags, addedTags, deletedTags]);

  React.useEffect(() => {
    const handler = doNotAwait(async (event: Event) => {
      // @ts-expect-error there will be a detail
      const ids: Array<string> = event.detail.ids;
      setSelectedIds(ids.map((x) => parseInt(x, 10)));
      setSavedTagsMap({});
      setCommonTags(new RsSet([]));
      setAddedTags([]);
      setDeletedTags([]);
      try {
        const response = await axios.get<
          Array<{ recordId: number; tagMetaData: string | null }>
        >("/workspace/getTagsForRecords", {
          params: new URLSearchParams([["recordIds", ids.join(",")]]),
        });
        const newlyFetchedTags: Record<number, Array<Tag>> = Object.fromEntries(
          response.data.map(({ recordId, tagMetaData }) => [
            recordId,
            tagMetaData === null || tagMetaData === ""
              ? ([] as Array<Tag>)
              : parseEncodedTags(tagMetaData.split(",")),
          ]),
        );
        setSavedTagsMap(newlyFetchedTags);
        const newCommonTags = flattenWithIntersectionWithEq(
          new RsSet(
            Object.values(newlyFetchedTags).map((tags) => new RsSet(tags)),
          ),
          areSameTag,
        );
        setCommonTags(newCommonTags);
      } catch (error) {
        if (error instanceof Error) {
          console.error(error);
          addAlert(
            mkAlert({
              variant: "error",
              title: "Could not get tags.",
              message: error.message,
            }),
          );
        }
      }
    });
    window.addEventListener("OPEN_TAG_DIALOG", handler);
    return () => {
      window.removeEventListener("OPEN_TAG_DIALOG", handler);
    };
  }, []);

  const handleSave = async () => {
    if (selectedIds === null || selectedIds.length === 0) return; // input type should be non-empty list?
    setSaving(true);
    try {
      const postData = ArrayUtils.all(
        Object.entries(savedTagsMap).map(([recordId, savedTags]) =>
          encodeTags([
            ...new RsSet(savedTags)
              .unionWithEq(new RsSet(addedTags), areSameTag)
              .subtractWithEq(new RsSet(deletedTags), areSameTag),
          ]).map((tagMetaData) => ({ recordId, tagMetaData })),
        ),
      ).orElseGet(() => {
        throw new Error("Some tags are invalid");
      });
      await axios.post<unknown>("/workspace/saveTagsForRecords", postData);
    } catch (e) {
      const error = e as any;
      if (Array.isArray(error.response?.data.errorMessages)) {
        if (error.response.data.errorMessages.length === 1)
          throw new Error(error.response.data.errorMessages[0]);
        throw new AggregateError(
          error.response.data.errorMessages.map(
            (msg: string) => new Error(msg),
          ),
        );
      }
      throw e;
    } finally {
      setSaving(false);
    }
  };

  React.useEffect(() => {
    const handler = doNotAwait(async () => {
      try {
        const { data } = await axios.get<boolean>(
          "/userform/ajax/enforcedOntologies",
        );
        setEnforcedOntologies(data);
      } catch (e) {
        if (e instanceof Error) {
          console.error(e);
          addAlert(
            mkAlert({
              title: "Could not determine if ontologies are enforced or not.",
              message: e.message,
              variant: "error",
            }),
          );
        }
      }
    });
    window.addEventListener("OPEN_TAG_DIALOG", handler);
    return () => {
      window.removeEventListener("OPEN_TAG_DIALOG", handler);
    };
  }, []);

  return (
    <Dialog open={selectedIds !== null} onClose={() => setSelectedIds(null)}>
      <DialogTitle>
        {selectedIds === null ? (
          <>{/* never shown due to open prop */}</>
        ) : (
          <>
            Tagging {selectedIds.length} item{selectedIds.length > 1 && "s"}
          </>
        )}
      </DialogTitle>
      <DialogContent>
        <Grid container direction="column" spacing={2}>
          <Grid item>
            <Typography variant="body2">
              You can tag Documents, Notebooks, and Folders to categorise work
              and make it more searchable. If you&apos;ve selected multiple
              items, only shared tags are shown.{" "}
              <a href={docLinks.tags} rel="noreferrer" target="_blank">
                Read more about creating, importing, and using Tags here.
              </a>{" "}
            </Typography>
          </Grid>
          <Grid item>
            <TagListing
              tags={
                savedTagsMap === null
                  ? []
                  : [
                      ...commonTags.subtractWithEq(
                        new RsSet(deletedTags),
                        areSameTag,
                      ),
                      ...new RsSet(addedTags).subtractWithEq(
                        new RsSet(deletedTags),
                        areSameTag,
                      ),
                    ]
              }
              onDelete={(index, tag) => {
                setDeletedTags([...deletedTags, tag]);
                setAddedTags(addedTags.filter((aTag) => aTag !== tag));
              }}
              endAdornment={
                <Grid item>
                  <AddTag
                    enforceOntologies={enforcedOntologies ?? undefined}
                    onSelection={(
                      tag:
                        | Tag
                        | {
                            value: string;
                            vocabulary: string;
                            uri: string;
                            version: string;
                          },
                    ) => {
                      /*
                       * If ontologies are being enforced then it is guarateed that a
                       * selected tag will have vocabulary, a URI, and a version and as
                       * such those properties are not wrapped in Optionals. To make all of
                       * the logic in this module simpler, we normalised the selected tag
                       * into a form that will always wrap those properties.
                       */
                      const normalisedTag: Tag = {
                        value: tag.value,
                        vocabulary:
                          tag.vocabulary instanceof Optional
                            ? tag.vocabulary
                            : Optional.present(tag.vocabulary),
                        uri:
                          tag.uri instanceof Optional
                            ? tag.uri
                            : Optional.present(tag.uri),
                        version:
                          tag.version instanceof Optional
                            ? tag.version
                            : Optional.present(tag.version),
                      };
                      // condition prevents duplicates
                      if (
                        !commonTags
                          .unionWithEq(new RsSet(addedTags), areSameTag)
                          .hasWithEq(normalisedTag, areSameTag)
                      ) {
                        setAddedTags([...addedTags, normalisedTag]);
                        setDeletedTags(
                          deletedTags.filter(
                            (dTag) => !areSameTag(dTag, normalisedTag),
                          ),
                        );
                      }
                    }}
                    value={
                      savedTagsMap === null ? ([] as Array<Tag>) : visibleTags
                    }
                    disabled={
                      Object.keys(savedTagsMap).length === 0 ||
                      enforcedOntologies === null
                    }
                  />
                </Grid>
              }
            />
          </Grid>
        </Grid>
      </DialogContent>
      <DialogActions>
        <Button onClick={() => setSelectedIds(null)}>Cancel</Button>
        <SubmitSpinnerButton
          disabled={
            saving || (addedTags.length === 0 && deletedTags.length === 0)
          }
          onClick={doNotAwait(async () => {
            try {
              await handleSave();
              trackEvent("user:tag:documents:workspace", {
                count: selectedIds?.length,
              });
              setSelectedIds(null);
              addAlert(
                mkAlert({
                  variant: "success",
                  message: "Successfully saved tags.",
                }),
              );
            } catch (e) {
              let error: Error;
              let details: Array<AlertDetails> = [];
              if (!(e instanceof Error)) {
                error = new Error("An unknown error occurred.");
              } else {
                error = e;
              }
              let message = error.message;
              if (error instanceof AggregateError) {
                details = ArrayUtils.filterClass(Error, [...error.errors]).map(
                  (e: Error) => ({
                    title: e.message,
                    variant: "error",
                  }),
                );
                message = "There are multiple errors.";
              }
              console.error(error);
              addAlert(
                mkAlert({
                  variant: "error",
                  title: "Could not save tags.",
                  message,
                  ...(details.length === 0 ? {} : { details }),
                }),
              );
            }
          })}
          loading={saving}
          label="Save"
        />
      </DialogActions>
    </Dialog>
  );
}
