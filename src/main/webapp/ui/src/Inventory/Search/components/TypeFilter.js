//@flow

import React, { type Node, useContext } from "react";
import SearchContext from "../../../stores/contexts/Search";
import ListItemText from "@mui/material/ListItemText";
import ListItemIcon from "@mui/material/ListItemIcon";
import { StyledMenu, StyledMenuItem } from "../../../components/StyledMenu";
import { type ResultType } from "../../../stores/definitions/Search";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { library } from "@fortawesome/fontawesome-svg-core";
import { faCircle } from "@fortawesome/free-regular-svg-icons";
import { match } from "../../../util/Util";
import RecordTypeIcon from "../../../components/RecordTypeIcon";
library.add(faCircle);
import { useTheme } from "@mui/material/styles";

type TypeFilterArgs = {|
  anchorEl: ?HTMLElement,
  current: ResultType,
  onClose: (ResultType) => void,
|};

export default function TypeFilter({
  anchorEl,
  onClose,
  current,
}: TypeFilterArgs): Node {
  const { search } = useContext(SearchContext);
  const theme = useTheme();

  return (
    <div data-test-id="typeDropdown">
      <StyledMenu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={() => {
          onClose(current);
        }}
      >
        <StyledMenuItem
          selected={current === "ALL"}
          aria-current={current === "ALL"}
          onClick={() => {
            onClose("ALL");
          }}
          disabled={!search.allowedTypeFilters.has("ALL")}
          data-test-id="typeAll"
        >
          <ListItemIcon>
            <FontAwesomeIcon icon={["far", "circle"]} />
          </ListItemIcon>
          <ListItemText
            primary="All"
            secondary={
              search.fetcher.allTypesAllowed
                ? null
                : "Enter a search query first."
            }
          />
        </StyledMenuItem>
        <StyledMenuItem
          selected={current === "CONTAINER"}
          aria-current={current === "CONTAINER"}
          onClick={() => {
            onClose("CONTAINER");
          }}
          disabled={!search.allowedTypeFilters.has("CONTAINER")}
          data-test-id="containerType"
        >
          <ListItemIcon>
            <RecordTypeIcon
              record={{
                recordTypeLabel: "Container",
                iconName: "container",
              }}
              color={theme.palette.standardIcon}
            />
          </ListItemIcon>
          <ListItemText primary="Containers" />
        </StyledMenuItem>
        <StyledMenuItem
          selected={current === "SAMPLE"}
          aria-current={current === "SAMPLE"}
          onClick={() => {
            onClose("SAMPLE");
          }}
          disabled={!search.allowedTypeFilters.has("SAMPLE")}
          data-test-id="sampleType"
        >
          <ListItemIcon>
            <RecordTypeIcon
              record={{
                recordTypeLabel: "Sample",
                iconName: "sample",
              }}
              color={theme.palette.standardIcon}
            />
          </ListItemIcon>
          <ListItemText
            primary="Samples"
            secondary={match<void, string>([
              [() => search.benchSearch, "Samples cannot be found on benches."],
              [
                () => search.fetcher.parentIsContainer,
                "Samples cannot be found in containers.",
              ],
              [() => true, ""],
            ])()}
          />
        </StyledMenuItem>
        <StyledMenuItem
          selected={current === "SUBSAMPLE"}
          aria-current={current === "SUBSAMPLE"}
          onClick={() => {
            onClose("SUBSAMPLE");
          }}
          disabled={!search.allowedTypeFilters.has("SUBSAMPLE")}
          data-test-id="subsampleType"
        >
          <ListItemIcon>
            <RecordTypeIcon
              record={{
                recordTypeLabel: "Subsample",
                iconName: "subsample",
              }}
              color={theme.palette.standardIcon}
            />
          </ListItemIcon>
          <ListItemText primary="Subsamples" />
        </StyledMenuItem>
        <StyledMenuItem
          selected={current === "TEMPLATE"}
          aria-current={current === "TEMPLATE"}
          onClick={() => {
            onClose("TEMPLATE");
          }}
          disabled={!search.allowedTypeFilters.has("TEMPLATE")}
          data-test-id="templateType"
        >
          <ListItemIcon>
            <RecordTypeIcon
              record={{
                recordTypeLabel: "Template",
                iconName: "template",
              }}
              color={theme.palette.standardIcon}
              style={{
                height: 18,
                width: 18,
              }}
            />
          </ListItemIcon>
          <ListItemText
            primary="Templates"
            secondary={match<void, string>([
              [
                () => search.benchSearch,
                "Templates cannot be found on benches.",
              ],
              [
                () => search.fetcher.parentIsContainer,
                "Templates cannot be found in containers.",
              ],
              [() => true, ""],
            ])()}
          />
        </StyledMenuItem>
      </StyledMenu>
    </div>
  );
}
