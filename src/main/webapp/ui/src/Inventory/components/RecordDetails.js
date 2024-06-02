// @flow

import DescriptionList from "../../components/DescriptionList";
import DOMPurify from "dompurify";
import TableCellBlank from "../../components/TableCellBlank";
import TimeAgoCustom from "../../components/TimeAgoCustom";
import { type Record } from "../../stores/definitions/Record";
import useStores from "../../stores/use-stores";
import { formatFileSize } from "../../util/files";
import ContentsChips from "../Container/Content/ContentsChips";
import Breadcrumbs from "./Breadcrumbs";
import GlobalId from "../../components/GlobalId";
import { RecordLink } from "./RecordLink";
import Avatar from "@mui/material/Avatar";
import Grid from "@mui/material/Grid";
import { makeStyles } from "tss-react/mui";
import { observer } from "mobx-react-lite";
import React, { type Node, type ComponentType, useContext } from "react";
import TagListing from "../../components/Tags/TagListing";
import NavigateContext from "../../stores/contexts/Navigate";

const useStyles = makeStyles()((theme) => ({
  label: {
    color: theme.palette.text.secondary,
  },
  image: {
    height: 150,
    width: 150,
    margin: "auto",
    "& img": {
      objectFit: "contain",
    },
    borderRadius: 0,
  },
  grow: {
    flexGrow: 1,
  },
}));

type RecordDetailsArgs = {|
  record: Record,
  hideName?: boolean,
|};

function RecordDetails({ record, hideName = false }: RecordDetailsArgs): Node {
  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();
  const { uiStore } = useStores();
  const { classes } = useStyles();

  return (
    <Grid
      container
      direction={uiStore.isVerySmall ? "column" : "row"}
      wrap="nowrap"
      alignContent="center"
    >
      {record.thumbnail && (
        <Grid item>
          <Avatar src={record.thumbnail} className={classes.image} />
        </Grid>
      )}
      <Grid item className={classes.grow}>
        <DescriptionList
          rightAlignDds
          /* some elements are not displayed depending on view (public or limited) */
          content={[
            {
              label: "Global ID",
              value: record.globalId ? (
                <GlobalId record={record} />
              ) : (
                <TableCellBlank />
              ),
              reducedPadding: true,
            },
            ...(hideName
              ? []
              : [
                  {
                    label: "Name",
                    value: record.name,
                  },
                ]),
            ...(record.recordDetails.quantity
              ? [
                  {
                    label: "Quantity",
                    value: record.recordDetails.quantity,
                  },
                ]
              : []),
            ...(record.readAccessLevel !== "public" &&
            record.recordDetails.sample
              ? [
                  {
                    label: "Sample",
                    value: (
                      <RecordLink
                        record={record.recordDetails.sample}
                        overflow
                      />
                    ),
                    reducedPadding: true,
                  },
                ]
              : []),
            ...(record.recordDetails.version
              ? [
                  {
                    label: "Version",
                    value: record.recordDetails.version,
                  },
                ]
              : []),
            ...(record.recordDetails.description
              ? [
                  {
                    label: "Description",
                    value: (
                      <span
                        dangerouslySetInnerHTML={{
                          __html: DOMPurify.sanitize(
                            record.recordDetails.description,
                            { ADD_ATTR: ["target"] }
                          ),
                        }}
                      ></span>
                    ),
                  },
                ]
              : []),
            ...(record.recordDetails.tags
              ? [
                  {
                    label: "Tags",
                    value: (
                      <TagListing
                        onClick={(tag) => {
                          navigate(
                            `/inventory/search?query=l: (tags:"${tag.value}")`
                          );
                          uiStore.setVisiblePanel("left");
                        }}
                        tags={record.recordDetails.tags}
                        size="small"
                        // metadata popup is not shown because a popup inside a
                        // popup is likely to be confusing
                        showMetadataPopup={false}
                      />
                    ),
                  },
                ]
              : []),
            ...(record.readAccessLevel === "full" &&
            record.recordDetails.contents
              ? [
                  {
                    label: "Contents",
                    value: (
                      <ContentsChips record={record.recordDetails.contents} />
                    ),
                    reducedPadding: true,
                  },
                ]
              : []),
            ...(record.readAccessLevel !== "public" &&
            record.recordDetails.location
              ? [
                  {
                    label: "Location",
                    value: (
                      <Breadcrumbs record={record.recordDetails.location} />
                    ),
                    reducedPadding: true,
                  },
                ]
              : []),
            ...(record.readAccessLevel === "full" &&
            record.recordDetails.modified
              ? [
                  {
                    label: "Modified",
                    value: (
                      <>
                        <TimeAgoCustom
                          time={record.recordDetails.modified[0]}
                          formatter={(value, unit, suffix) =>
                            `${value}${unit[0]} ${suffix}`
                          }
                        />{" "}
                        by {record.recordDetails.modified[1]}
                      </>
                    ),
                  },
                ]
              : []),
            ...(record.recordDetails.owner
              ? [
                  {
                    label: "Owner",
                    value: record.recordDetails.owner,
                  },
                ]
              : []),
            ...(record.recordDetails.size
              ? [
                  {
                    label: "Size",
                    value: formatFileSize(record.recordDetails.size),
                  },
                ]
              : []),
          ]}
          dividers
        />
      </Grid>
    </Grid>
  );
}

export default (observer(RecordDetails): ComponentType<RecordDetailsArgs>);
