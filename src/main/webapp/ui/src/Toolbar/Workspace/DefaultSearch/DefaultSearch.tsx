import { faBookmark } from "@fortawesome/free-solid-svg-icons/faBookmark";
import { faCaretDown } from "@fortawesome/free-solid-svg-icons/faCaretDown";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Box from "@mui/material/Box";
import IconButton from "@mui/material/IconButton";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import Tooltip from "@mui/material/Tooltip";
import React from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import ConfirmProvider, { useConfirm } from "@/components/ConfirmProvider";
import useUiPreference, { fetchPreferences, PREFERENCES, UiPreferences } from "@/hooks/api/useUiPreference";
import { RECORD_LIST_SHOWN, workspaceSettings } from "./workspaceGlobals";
import { parseSearchQuery, type SummaryLabels, summariseSearch, toSearchQuery } from "./workspaceSearchUrl";

function currentSearchQuery(): string | null {
  const settings = workspaceSettings();
  return settings?.searchMode ? toSearchQuery(settings) : null;
}

function useCurrentSearchQuery(): string | null {
  const [current, setCurrent] = React.useState(currentSearchQuery);
  React.useEffect(() => {
    const update = () => setCurrent(currentSearchQuery());
    // The preferences can finish loading after the first list is shown
    update();
    window.addEventListener(RECORD_LIST_SHOWN, update);
    return () => window.removeEventListener(RECORD_LIST_SHOWN, update);
  }, []);
  return current;
}

function useSummary(): (query: string) => string {
  const { t } = useTranslation("workspace");
  return (query) => {
    const parsed = parseSearchQuery(`?${query}`);
    if (parsed.kind !== "search") return "";
    const labels: SummaryLabels = {
      options: {
        global: t("toolbar.simpleSearch.filterTypes.global"),
        fullText: t("toolbar.simpleSearch.filterTypes.fullText"),
        tag: t("toolbar.simpleSearch.filterTypes.tag"),
        name: t("toolbar.simpleSearch.filterTypes.name"),
        created: t("toolbar.simpleSearch.filterTypes.created"),
        lastModified: t("toolbar.simpleSearch.filterTypes.lastModified"),
        form: t("toolbar.simpleSearch.filterTypes.form"),
        template: t("toolbar.simpleSearch.filterTypes.template"),
        attachment: t("toolbar.simpleSearch.filterTypes.attachment"),
        owner: t("toolbar.simpleSearch.filterTypes.owner"),
        records: t("toolbar.defaultSearch.withinRecords"),
      },
      filters: {
        sharedFilter: t("toolbar.workspaceToolbar.sharedWithMe"),
        favoritesFilter: t("toolbar.workspaceToolbar.favorites"),
        viewableItemsFilter: t("toolbar.workspaceToolbar.viewAll"),
        templatesFilter: t("toolbar.workspaceToolbar.templates"),
        ontologiesFilter: t("toolbar.workspaceToolbar.ontologyFiles"),
      },
      operators: { AND: t("toolbar.defaultSearch.operatorAnd"), OR: t("toolbar.defaultSearch.operatorOr") },
      dateRange: (from, to) => t("toolbar.defaultSearch.dateRange", { from: from ?? "…", to: to ?? "…" }),
    };
    return summariseSearch(parsed.state, labels);
  };
}

/** The default as stored now, which another tab may have changed since this page loaded. */
async function readStoredDefault(fallback: string | null): Promise<string | null> {
  try {
    const preferences = await fetchPreferences();
    if (typeof preferences !== "object") return null;
    const key = Symbol.keyFor(PREFERENCES.WORKSPACE_DEFAULT_SEARCH) as string;
    return (preferences[key] as { value?: string | null } | undefined)?.value ?? null;
  } catch {
    return fallback;
  }
}

type DefaultSearchProps = {
  /** Opens a URL. Tests replace it to observe the navigation. */
  navigate?: (url: string) => void;
};

function DefaultSearchControls({ navigate }: Required<DefaultSearchProps>): React.ReactNode {
  const { t } = useTranslation("workspace");
  const [loadedDefault, setStored] = useUiPreference<string | null>(PREFERENCES.WORKSPACE_DEFAULT_SEARCH, {
    defaultValue: null,
  });
  const [stored, setShown] = React.useState(loadedDefault);
  const current = useCurrentSearchQuery();
  const summarise = useSummary();
  const confirm = useConfirm();
  const [menuAnchor, setMenuAnchor] = React.useState<HTMLElement | null>(null);
  const slot = document.getElementById("defaultSearchSlot");

  const store = (query: string | null) => {
    setShown(query);
    setStored(query);
  };

  const confirmReplacing = (query: string) =>
    confirm(
      t("toolbar.defaultSearch.replaceTitle"),
      <>
        {t("toolbar.defaultSearch.replaceMessage")}
        <Box component="span" sx={{ display: "block", mt: 1, fontStyle: "italic" }}>
          {summarise(query)}
        </Box>
      </>,
      t("toolbar.defaultSearch.replaceConfirm"),
    );

  const save = async (query: string) => {
    const latest = await readStoredDefault(stored);
    setShown(latest);
    if (latest === query) return;
    if (latest === null || (await confirmReplacing(latest))) store(query);
  };

  const remove = async () => {
    const latest = await readStoredDefault(stored);
    if (latest === null) {
      setShown(null);
      return;
    }
    if (latest !== stored) {
      setShown(latest);
      const confirmed = await confirm(
        t("toolbar.defaultSearch.removeTitle"),
        <>
          {t("toolbar.defaultSearch.removeMessage")}
          <Box component="span" sx={{ display: "block", mt: 1, fontStyle: "italic" }}>
            {summarise(latest)}
          </Box>
        </>,
        t("toolbar.defaultSearch.removeConfirm"),
      );
      if (!confirmed) return;
    }
    store(null);
  };

  let bar: React.ReactNode = null;
  if (current !== null && current === stored) {
    bar = <span id="isDefaultSearch">{t("toolbar.defaultSearch.isDefault")}</span>;
  } else if (current !== null) {
    bar = (
      <button type="button" id="saveDefaultSearch" onClick={() => void save(current)}>
        {t("toolbar.defaultSearch.save")}
      </button>
    );
  }

  return (
    <>
      {stored !== null && (
        <>
          <Tooltip title={summarise(stored)} enterDelay={300} describeChild>
            <IconButton
              color="inherit"
              aria-label={t("toolbar.defaultSearch.run")}
              onClick={() => navigate(`/workspace?${stored}`)}
            >
              <FontAwesomeIcon icon={faBookmark} />
            </IconButton>
          </Tooltip>
          <IconButton
            color="inherit"
            size="small"
            aria-label={t("toolbar.defaultSearch.options")}
            aria-haspopup="menu"
            aria-controls={menuAnchor ? "defaultSearchMenu" : undefined}
            aria-expanded={menuAnchor ? "true" : undefined}
            onClick={(e) => setMenuAnchor(e.currentTarget)}
          >
            <FontAwesomeIcon icon={faCaretDown} />
          </IconButton>
          <Menu
            id="defaultSearchMenu"
            anchorEl={menuAnchor}
            open={menuAnchor !== null}
            onClose={() => setMenuAnchor(null)}
          >
            <MenuItem
              onClick={() => {
                setMenuAnchor(null);
                void remove();
              }}
            >
              {t("toolbar.defaultSearch.remove")}
            </MenuItem>
          </Menu>
        </>
      )}
      {slot && createPortal(bar, slot)}
    </>
  );
}

/**
 * The "Default search" toolbar button, and the control in the search bar that
 * saves the current search as the default.
 */
export default function DefaultSearch({
  navigate = (url) => window.location.assign(url),
}: DefaultSearchProps): React.ReactNode {
  return (
    <UiPreferences>
      <ConfirmProvider>
        <DefaultSearchControls navigate={navigate} />
      </ConfirmProvider>
    </UiPreferences>
  );
}
