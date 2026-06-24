import { faCircle } from "@fortawesome/free-regular-svg-icons/faCircle";
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
                recordTypeLabel: t("search.controls.type.container"),
                iconName: "container",
              }}
              color={theme.palette.standardIcon.main}
            />
          </ListItemIcon>
          <ListItemText primary={t("search.controls.type.containers")} />
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
                recordTypeLabel: t("search.controls.type.sample"),
                iconName: "sample",
              }}
              color={theme.palette.standardIcon.main}
            />
          </ListItemIcon>
          <ListItemText
            primary={t("search.controls.type.samples")}
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
                recordTypeLabel: t("search.controls.type.subsample"),
                iconName: "subsample",
              }}
              color={theme.palette.standardIcon.main}
            />
          </ListItemIcon>
          <ListItemText primary={t("search.controls.type.subsamples")} />
        </MenuItem>
        <MenuItem
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
                recordTypeLabel: t("search.controls.type.template"),
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
            primary={t("search.controls.type.templates")}
            secondary={match<void, string>([
              [() => search.benchSearch, t("search.controls.type.templatesNotOnBenches")],
              [() => search.fetcher.parentIsContainer, t("search.controls.type.templatesNotInContainers")],
              [() => true, ""],
            ])()}
          />
        </MenuItem>
      </StyledMenu>
    </div>
  );
}
