import Avatar from "@mui/material/Avatar";
import Grid from "@mui/material/Grid";
import DOMPurify from "dompurify";
import { observer } from "mobx-react-lite";
import { type ReactNode, useContext } from "react";
import { useTranslation } from "react-i18next";
import TimeAgoCustom from "@/components/TimeAgoCustom";
import DescriptionList from "../../components/DescriptionList";
import GlobalId from "../../components/GlobalId";
import TableCellBlank from "../../components/TableCellBlank";
import TagListing from "../../components/Tags/TagListing";
import NavigateContext from "../../stores/contexts/Navigate";
import type { Record } from "../../stores/definitions/Record";
import useStores from "../../stores/use-stores";
import { formatFileSize } from "../../util/files";
import ContentsChips from "../Container/Content/ContentsChips";
import Breadcrumbs from "./Breadcrumbs";
import { RecordLink } from "./RecordLink";

type RecordDetailsArgs = {
  record: Record;
  hideName?: boolean;
};

function RecordDetails({ record, hideName = false }: RecordDetailsArgs): ReactNode {
  const { t } = useTranslation("inventory");
  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();
  const { uiStore } = useStores();

  return (
    <Grid
      container
      sx={{
        flexDirection: uiStore.isVerySmall ? "column" : "row",
        flexWrap: "nowrap",
        alignContent: "center",
      }}
    >
      {record.thumbnail && (
        <Grid>
          <Avatar
            src={record.thumbnail}
            sx={{
              height: 150,
              width: 150,
              m: "auto",
              borderRadius: 0,
              "& img": {
                objectFit: "contain",
              },
            }}
          />
        </Grid>
      )}
      <Grid sx={{ flexGrow: 1 }}>
        <DescriptionList
          /* some elements are not displayed depending on view (public or limited) */
          content={[
            ...(record.recordDetails.hideGlobalId
              ? []
              : [
                  {
                    label: t("recordDetails.labels.globalId"),
                    value: record.globalId ? <GlobalId record={record} /> : <TableCellBlank />,
                    reducedPadding: true,
                  },
                ]),
            ...(hideName
              ? []
              : [
                  {
                    label: t("recordDetails.labels.name"),
                    value: record.name,
                  },
                ]),
            ...(record.recordDetails.quantity
              ? [
                  {
                    label: t("recordDetails.labels.quantity"),
                    value: record.recordDetails.quantity,
                  },
                ]
              : []),
            ...(record.readAccessLevel !== "public" && record.recordDetails.sample
              ? [
                  {
                    label: t("recordDetails.labels.sample"),
                    value: <RecordLink record={record.recordDetails.sample} overflow />,
                    reducedPadding: true,
                  },
                ]
              : []),
            ...(record.recordDetails.version
              ? [
                  {
                    label: t("recordDetails.labels.version"),
                    value: record.recordDetails.version,
                  },
                ]
              : []),
            ...(record.recordDetails.description
              ? [
                  {
                    label: t("recordDetails.labels.description"),
                    value: (
                      <span
                        dangerouslySetInnerHTML={{
                          __html: DOMPurify.sanitize(record.recordDetails.description, { ADD_ATTR: ["target"] }),
                        }}
                      ></span>
                    ),
                  },
                ]
              : []),
            ...(record.recordDetails.tags
              ? [
                  {
                    label: t("recordDetails.labels.tags"),
                    value: (
                      <TagListing
                        onClick={(tag) => {
                          navigate(`/inventory/search?query=l: (tags:"${tag.value}")`);
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
            ...(record.readAccessLevel === "full" && record.recordDetails.contents
              ? [
                  {
                    label: t("recordDetails.labels.contents"),
                    value: <ContentsChips record={record.recordDetails.contents} />,
                    reducedPadding: true,
                  },
                ]
              : []),
            ...(record.readAccessLevel !== "public" && record.recordDetails.location
              ? [
                  {
                    label: t("recordDetails.labels.location"),
                    value: <Breadcrumbs record={record.recordDetails.location} />,
                    reducedPadding: true,
                  },
                ]
              : []),
            ...(record.readAccessLevel === "full" && record.recordDetails.modified
              ? [
                  {
                    label: t("recordDetails.labels.modified"),
                    value: (
                      <>
                        <TimeAgoCustom time={record.recordDetails.modified[0]} compact />{" "}
                        {t("recordDetails.modifiedBy", { user: record.recordDetails.modified[1] })}
                      </>
                    ),
                  },
                ]
              : []),
            ...(record.recordDetails.owner
              ? [
                  {
                    label: t("recordDetails.labels.owner"),
                    value: record.recordDetails.owner,
                  },
                ]
              : []),
            ...(record.recordDetails.size
              ? [
                  {
                    label: t("recordDetails.labels.size"),
                    value: formatFileSize(record.recordDetails.size),
                  },
                ]
              : []),
            ...(record.recordDetails.galleryFile
              ? [
                  {
                    label: t("recordDetails.labels.galleryFile"),
                    value: <GlobalId record={record.recordDetails.galleryFile} />,
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

export default observer(RecordDetails);
