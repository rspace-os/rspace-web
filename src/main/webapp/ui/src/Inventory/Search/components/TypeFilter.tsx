import { faCircle } from "@fortawesome/free-regular-svg-icons/faCircle";
import { faMicroscope } from "@fortawesome/free-solid-svg-icons/faMicroscope";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import { useTheme } from "@mui/material/styles";
import type React from "react";
import { useContext } from "react";
import RecordTypeIcon from "../../../components/RecordTypeIcon";
import StyledMenu from "../../../components/StyledMenu";
import SearchContext from "../../../stores/contexts/Search";
import type { ResultType } from "../../../stores/definitions/Search";
import { match } from "../../../util/Util";

type TypeFilterArgs = {
  anchorEl: HTMLElement | null;
  current: ResultType;
  onClose: (newTypeFilter: ResultType) => void;
};

export default function TypeFilter({ anchorEl, onClose, current }: TypeFilterArgs): React.ReactNode {
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
        <MenuItem
          selected={current === "ALL"}
          aria-current={current === "ALL"}
          onClick={() => {
            onClose("ALL");
          }}
          disabled={!search.allowedTypeFilters.has("ALL")}
          data-test-id="typeAll"
        >
          <ListItemIcon>
            <FontAwesomeIcon icon={faCircle} />
          </ListItemIcon>
          <ListItemText
            primary="All"
            secondary={search.fetcher.allTypesAllowed ? null : "Enter a search query first."}
          />
        </MenuItem>
        <MenuItem
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
              color={theme.palette.standardIcon.main}
            />
          </ListItemIcon>
          <ListItemText primary="Containers" />
        </MenuItem>
        <MenuItem
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
              color={theme.palette.standardIcon.main}
            />
          </ListItemIcon>
          <ListItemText
            primary="Samples"
            secondary={match<void, string>([
              [() => search.benchSearch, "Samples cannot be found on benches."],
              [() => search.fetcher.parentIsContainer, "Samples cannot be found in containers."],
              [() => true, ""],
            ])()}
          />
        </MenuItem>
        <MenuItem
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
              color={theme.palette.standardIcon.main}
            />
          </ListItemIcon>
          <ListItemText primary="Subsamples" />
        </MenuItem>
        <MenuItem
          selected={current === "INSTRUMENT"}
          aria-current={current === "INSTRUMENT"}
          onClick={() => {
            onClose("INSTRUMENT");
          }}
          disabled={!search.allowedTypeFilters.has("INSTRUMENT")}
          data-test-id="instrumentType"
        >
          <ListItemIcon>
            <FontAwesomeIcon icon={faMicroscope} color={theme.palette.standardIcon.main} style={{ fontSize: "1em" }} />
          </ListItemIcon>
          <ListItemText primary="Instruments" />
        </MenuItem>
        <MenuItem
          selected={current === "SAMPLE_TEMPLATE"}
          aria-current={current === "SAMPLE_TEMPLATE"}
          onClick={() => {
            onClose("SAMPLE_TEMPLATE");
          }}
          disabled={!search.allowedTypeFilters.has("SAMPLE_TEMPLATE")}
          data-test-id="templateType"
        >
          <ListItemIcon>
            <RecordTypeIcon
              record={{
                recordTypeLabel: "Template",
                iconName: "template",
              }}
              color={theme.palette.standardIcon.main}
              style={{
                height: 18,
                width: 18,
              }}
            />
          </ListItemIcon>
          <ListItemText
            primary="Sample Templates"
            secondary={match<void, string>([
              [() => search.benchSearch, "Sample Templates cannot be found on benches."],
              [() => search.fetcher.parentIsContainer, "Sample Templates cannot be found in containers."],
              [() => true, ""],
            ])()}
          />
        </MenuItem>
        <MenuItem
          selected={current === "INSTRUMENT_TEMPLATE"}
          aria-current={current === "INSTRUMENT_TEMPLATE"}
          onClick={() => {
            onClose("INSTRUMENT_TEMPLATE");
          }}
          disabled={!search.allowedTypeFilters.has("INSTRUMENT_TEMPLATE")}
          data-test-id="instrumentTemplateType"
        >
          <ListItemIcon>
            <RecordTypeIcon
              record={{
                recordTypeLabel: "Instrument Template",
                iconName: "instrumentTemplate",
              }}
              color={theme.palette.standardIcon.main}
              style={{
                height: 18,
                width: 18,
              }}
            />
          </ListItemIcon>
          <ListItemText
            primary="Instrument Templates"
            secondary={match<void, string>([
              [() => search.benchSearch, "Instrument Templates cannot be found on benches."],
              [() => search.fetcher.parentIsContainer, "Instrument Templates cannot be found in containers."],
              [() => true, ""],
            ])()}
          />
        </MenuItem>
      </StyledMenu>
    </div>
  );
}
