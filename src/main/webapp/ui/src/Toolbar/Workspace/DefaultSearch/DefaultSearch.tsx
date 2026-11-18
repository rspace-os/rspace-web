import { faBookmark } from "@fortawesome/free-solid-svg-icons/faBookmark";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Box from "@mui/material/Box";
import IconButton from "@mui/material/IconButton";
import Tooltip from "@mui/material/Tooltip";
import { visuallyHidden } from "@mui/utils";
import React from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import ConfirmProvider, { useConfirm } from "@/components/ConfirmProvider";
import useUiPreference, { fetchPreferences, PREFERENCES, UiPreferences } from "@/hooks/api/useUiPreference";
import { getFilterLabels } from "../SimpleSearch/SimpleSearch";
import { RECORD_LIST_SHOWN, SEARCH_INPUT_ID, VIEW_MODE_BUTTON_ID, workspaceSettings } from "./workspaceGlobals";
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
      options: { ...getFilterLabels(false), records: t("toolbar.defaultSearch.withinRecords") },
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
async function readStoredDefault(): Promise<string | null> {
  const preferences = await fetchPreferences();
  if (typeof preferences !== "object") return null;
  const key = Symbol.keyFor(PREFERENCES.WORKSPACE_DEFAULT_SEARCH) as string;
  return (preferences[key] as { value?: string | null } | undefined)?.value ?? null;
}

type DefaultSearchProps = {
  /** Opens a URL. Tests replace it to observe the navigation. */
  navigate?: (url: string) => void;
  /** Where the run button goes, inside the search box. Without one (tree view) it stays where this is rendered. */
  runButtonSlot?: HTMLElement | null;
};

function DefaultSearchControls({
  navigate,
  runButtonSlot,
}: Required<Pick<DefaultSearchProps, "navigate">> & Pick<DefaultSearchProps, "runButtonSlot">): React.ReactNode {
  const { t } = useTranslation("workspace");
  const [loadedDefault, setStored] = useUiPreference<string | null>(PREFERENCES.WORKSPACE_DEFAULT_SEARCH, {
    defaultValue: null,
  });
  const [stored, setShown] = React.useState(loadedDefault);
  const current = useCurrentSearchQuery();
  const summarise = useSummary();
  const confirm = useConfirm();
  const slot = document.getElementById("defaultSearchSlot");
  const runButtonRef = React.useRef<HTMLButtonElement>(null);
  const [announcement, setAnnouncement] = React.useState("");
  const [focusAfterStore, setFocusAfterStore] = React.useState<"run" | "search" | null>(null);

  React.useEffect(() => {
    if (focusAfterStore === "run") runButtonRef.current?.focus();
    // Tree view has no search box
    if (focusAfterStore === "search")
      (document.getElementById(SEARCH_INPUT_ID) ?? document.getElementById(VIEW_MODE_BUTTON_ID))?.focus();
    setFocusAfterStore(null);
  }, [focusAfterStore]);

  // Each refresh, save, remove and run moves this on, so a refresh that answers
  // after any of them is ignored rather than undoing what they show
  const latestCheck = React.useRef(0);

  // Another tab or window may have changed the default while this one was in the background.
  // Moving between two visible windows changes focus but not visibility.
  React.useEffect(() => {
    const refresh = () => {
      if (document.visibilityState === "hidden") return;
      const check = ++latestCheck.current;
      readStoredDefault().then(
        (latest) => {
          if (check === latestCheck.current) setShown(latest);
        },
        () => {
          // Keep showing the default this page has
        },
      );
    };
    document.addEventListener("visibilitychange", refresh);
    window.addEventListener("focus", refresh);
    return () => {
      document.removeEventListener("visibilitychange", refresh);
      window.removeEventListener("focus", refresh);
    };
  }, []);

  const [saving, setSaving] = React.useState(false);

  const store = async (query: string | null) => {
    latestCheck.current++;
    setSaving(true);
    try {
      await setStored(query);
      setShown(query);
      setAnnouncement(query === null ? t("toolbar.defaultSearch.removed") : t("toolbar.defaultSearch.saved"));
      setFocusAfterStore(query === null ? "search" : "run");
    } catch {
      setAnnouncement(query === null ? t("toolbar.defaultSearch.removeFailed") : t("toolbar.defaultSearch.saveFailed"));
    } finally {
      setSaving(false);
    }
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

  // Without the stored value, saving or removing could overwrite another tab's default unasked
  const readOrAnnounce = async (failed: string): Promise<{ latest: string | null } | null> => {
    latestCheck.current++;
    try {
      return { latest: await readStoredDefault() };
    } catch {
      setAnnouncement(failed);
      return null;
    }
  };

  const save = async (query: string) => {
    const read = await readOrAnnounce(t("toolbar.defaultSearch.saveFailed"));
    if (!read) return;
    const { latest } = read;
    setShown(latest);
    if (latest === query) {
      setAnnouncement(t("toolbar.defaultSearch.saved"));
      setFocusAfterStore("run");
      return;
    }
    if (latest === null || (await confirmReplacing(latest))) await store(query);
  };

  // Another tab may have changed the default since this page loaded
  const run = async () => {
    latestCheck.current++;
    let latest = stored;
    try {
      latest = await readStoredDefault();
    } catch {
      // Run the default this page loaded
    }
    setShown(latest);
    if (latest !== null) navigate(`/workspace?${latest}`);
  };

  const remove = async () => {
    const read = await readOrAnnounce(t("toolbar.defaultSearch.removeFailed"));
    if (!read) return;
    const { latest } = read;
    if (latest === null) {
      setShown(null);
      setAnnouncement(t("toolbar.defaultSearch.removed"));
      setFocusAfterStore("search");
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
    await store(null);
  };

  let bar: React.ReactNode = null;
  if (current !== null && current === stored) {
    bar = (
      <>
        <span id="isDefaultSearch">{t("toolbar.defaultSearch.isDefault")}</span>{" "}
        <button type="button" id="clearDefaultSearch" disabled={saving} onClick={() => void remove()}>
          {t("toolbar.defaultSearch.remove")}
        </button>
      </>
    );
  } else if (current !== null) {
    bar = (
      <button type="button" id="saveDefaultSearch" disabled={saving} onClick={() => void save(current)}>
        {t("toolbar.defaultSearch.save")}
      </button>
    );
  }

  const runButton = stored !== null && (
    <Tooltip title={summarise(stored)} enterDelay={300} describeChild>
      <IconButton
        ref={runButtonRef}
        disabled={saving}
        color="inherit"
        aria-label={t("toolbar.defaultSearch.run")}
        onClick={() => void run()}
      >
        <FontAwesomeIcon icon={faBookmark} />
      </IconButton>
    </Tooltip>
  );

  return (
    <>
      {runButton && (runButtonSlot ? createPortal(runButton, runButtonSlot) : runButton)}
      {slot && createPortal(bar, slot)}
      <Box component="span" role="status" sx={visuallyHidden}>
        {announcement}
      </Box>
    </>
  );
}

/**
 * The "Default search" button, and the controls in the search bar that save the
 * current search as the default or clear it.
 */
export default function DefaultSearch({
  navigate = (url) => window.location.assign(url),
  runButtonSlot,
}: DefaultSearchProps): React.ReactNode {
  return (
    <UiPreferences>
      <ConfirmProvider>
        <DefaultSearchControls navigate={navigate} runButtonSlot={runButtonSlot} />
      </ConfirmProvider>
    </UiPreferences>
  );
}
