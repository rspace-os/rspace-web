import { faSitemap } from "@fortawesome/free-solid-svg-icons/faSitemap";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import CancelIcon from "@mui/icons-material/Cancel";
import Box from "@mui/material/Box";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import Chip from "@mui/material/Chip";
import Grid from "@mui/material/Grid";
import IconButton from "@mui/material/IconButton";
import Link from "@mui/material/Link";
import Popover from "@mui/material/Popover";
import type React from "react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import type { Tag } from "../../stores/definitions/Tag";
import DescriptionList from "../DescriptionList";

const hasMetadata = (tag: Tag): boolean => tag.uri.isPresent() && tag.vocabulary.isPresent() && tag.version.isPresent();

export const searchHref = (tag: Tag): string =>
  `/inventory/search?${new URLSearchParams({ query: `l: (tags:"${tag.value}")` }).toString()}`;

type TagListingArgs = {
  tags: Array<Tag>;
  size?: "small" | "medium";

  /*
   * Additional content may be added inline to the end of the listing, such as
   * a button to add more tags. The content MUST be wrapped in a <Grid>
   */
  endAdornment?: React.ReactNode;

  showMetadataPopup?: boolean;
  onDelete?: (index: number, tag: Tag) => void;
  onClick?: (tag: Tag) => void;
};

/**
 * This component provides a listing of tags, with the ability to delete tags
 * if `onDelete` is true, to show the associated metadata if
 * `showMetadataPopup` is true, and to perform an action when a tag is clicked
 * if `onClick` is provided. A tag with metadata opens its metadata when
 * clicked, and the metadata offers `onClick` as a search link.
 */
export default function TagListing({
  tags,
  size = "medium",
  endAdornment,
  showMetadataPopup = true,
  onDelete,
  onClick,
}: TagListingArgs): React.ReactNode {
  const { t } = useTranslation("common");
  const [metadataPopup, setMetadataPopup] = useState<{
    anchorEl: HTMLElement;
    tag: Tag;
  } | null>(null);
  const cardContent = (tag: Tag) => [
    {
      label: t("tags.metadata.term"),
      value: tag.value,
    },
    {
      label: t("tags.metadata.termUri"),
      value: tag.uri.orElse("—"),
    },
    {
      label: t("tags.metadata.controlledVocabularyName"),
      value: tag.vocabulary.orElse("—"),
    },
    {
      label: t("tags.metadata.controlledVocabularyVersion"),
      value: tag.version.orElse("—"),
    },
  ];

  return (
    <Grid container direction="row" spacing={1}>
      {tags.map((tag, index) => (
        <Grid key={tag.uri.orElse(tag.value)}>
          {showMetadataPopup && hasMetadata(tag) ? (
            /*
             * The whole chip opens the metadata; delete is a sibling button laid
             * over the chip's end, so neither control is nested in the other.
             */
            <Box sx={{ position: "relative", display: "inline-flex", alignItems: "center", maxWidth: "100%" }}>
              <Chip
                size={size}
                variant="filled"
                icon={<FontAwesomeIcon icon={faSitemap} aria-hidden="true" />}
                label={tag.value}
                title={t("tags.metadata.view")}
                onClick={(e) => {
                  e.stopPropagation();
                  setMetadataPopup({ anchorEl: e.currentTarget, tag });
                }}
                sx={onDelete ? { pr: 2.5 } : undefined}
              />
              {onDelete && (
                <IconButton
                  size="small"
                  aria-label={t("tags.removeTag", { tag: tag.value })}
                  onClick={(e) => {
                    e.stopPropagation();
                    onDelete(index, tag);
                  }}
                  sx={{ position: "absolute", right: 4, p: 0, color: "inherit", opacity: 0.6 }}
                >
                  <CancelIcon fontSize="small" />
                </IconButton>
              )}
            </Box>
          ) : (
            <Chip
              label={tag.value}
              size={size}
              /*
               * When the chip is a clickable link to performing a search, we
               * set the `a` and `href` props so that the user can right-click
               * the chip and open it in a new tab/copy the link to the tag
               * search.
               */
              component={onDelete || !onClick ? "div" : "a"}
              variant={hasMetadata(tag) ? "filled" : "outlined"}
              sx={{
                /*
                 * By unsetting the userSelect style, when the chip is right
                 * clicked the browser's context menu provides the option of
                 * copying the link text, which is to say the tag's value.
                 */
                userSelect: "unset",
                cursor: onDelete || !onClick ? "default" : "pointer",
              }}
              {...(onDelete && { onDelete: () => onDelete(index, tag) })}
              {...(!onDelete &&
                onClick && {
                  href: searchHref(tag),
                  onClick: (e: React.MouseEvent<HTMLDivElement>) => {
                    e.preventDefault();
                    e.stopPropagation();
                    onClick(tag);
                  },
                })}
            />
          )}
        </Grid>
      ))}
      {metadataPopup && showMetadataPopup && (
        <Popover
          open={true}
          anchorEl={metadataPopup.anchorEl}
          anchorOrigin={{
            vertical: "top",
            horizontal: "right",
          }}
          transformOrigin={{
            vertical: "top",
            horizontal: "left",
          }}
          onClose={() => {
            setMetadataPopup(null);
          }}
          // React events bubble through the portal; keep them from a parent such as a table row.
          onClick={(e) => e.stopPropagation()}
        >
          <Card>
            <CardContent>
              <DescriptionList content={cardContent(metadataPopup.tag)} dividers={true} />
              {/* No search while editing: leaving would drop the form's unsaved changes. */}
              {onClick && !onDelete && (
                <Link
                  href={searchHref(metadataPopup.tag)}
                  onClick={(e) => {
                    if (e.ctrlKey || e.metaKey || e.shiftKey) return;
                    e.preventDefault();
                    setMetadataPopup(null);
                    onClick(metadataPopup.tag);
                  }}
                >
                  {t("tags.metadata.search")}
                </Link>
              )}
            </CardContent>
          </Card>
        </Popover>
      )}
      {endAdornment}
    </Grid>
  );
}
