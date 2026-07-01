import { faCircle } from "@fortawesome/free-regular-svg-icons/faCircle";
import { faMicroscope } from "@fortawesome/free-solid-svg-icons/faMicroscope";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import { useTheme } from "@mui/material/styles";
import type React from "react";
import { useContext } from "react";
import { useTranslation } from "react-i18next";
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
  const { t } = useTranslation("inventory");
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
            primary={t("search.controls.type.all")}
            secondary={search.fetcher.allTypesAllowed ? null : t("search.controls.type.enterQueryFirst")}
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
                recordTypeLabel: t("recordTypes.container.singular"),
                iconName: "container",
              }}
              color={theme.palette.standardIcon.main}
            />
          </ListItemIcon>
          <ListItemText primary={t("recordTypes.container.plural")} />
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
                recordTypeLabel: t("recordTypes.sample.singular"),
                iconName: "sample",
              }}
              color={theme.palette.standardIcon.main}
            />
          </ListItemIcon>
          <ListItemText
            primary={t("recordTypes.sample.plural")}
            secondary={match<void, string>([
              [() => search.benchSearch, t("search.controls.type.samplesNotOnBenches")],
              [() => search.fetcher.parentIsContainer, t("search.controls.type.samplesNotInContainers")],
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
                recordTypeLabel: t("recordTypes.subsample.singular"),
                iconName: "subsample",
              }}
              color={theme.palette.standardIcon.main}
            />
          </ListItemIcon>
          <ListItemText primary={t("recordTypes.subsample.plural")} />
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
          <ListItemText primary={t("recordTypes.instrument.plural")} />
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
                recordTypeLabel: t("recordTypes.template.singular"),
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
            primary={t("recordTypes.template.plural")}
            secondary={match<void, string>([
              [() => search.benchSearch, t("search.controls.type.templatesNotOnBenches")],
              [() => search.fetcher.parentIsContainer, t("search.controls.type.templatesNotInContainers")],
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
                recordTypeLabel: t("recordTypes.instrumentTemplate.singular"),
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
            primary={t("recordTypes.instrumentTemplate.plural")}
            secondary={match<void, string>([
              [() => search.benchSearch, t("search.controls.type.instrumentTemplatesNotOnBenches")],
              [() => search.fetcher.parentIsContainer, t("search.controls.type.instrumentTemplatesNotInContainers")],
              [() => true, ""],
            ])()}
          />
        </MenuItem>
      </StyledMenu>
    </div>
  );
}
