import Autocomplete from "@mui/material/Autocomplete";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { debounce } from "es-toolkit";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import GlobalId from "@/components/GlobalId";
import AlwaysNewFactory from "@/stores/models/Factory/AlwaysNewFactory";
import Search from "@/stores/models/Search";
import type TemplateModel from "@/stores/models/TemplateModel";

const SEARCH_DEBOUNCE_MS = 300;
// The backend rejects a search term shorter than this (422). Below it, show the initial list rather
// than sending a query that would only error.
const MIN_SEARCH_CHARS = 2;

/** `record` is the underlying model (null for a pre-filled placeholder that
 *  represents an already-chosen template not present in the current result page). */
type TemplateOption = { id: number; name: string; globalId: string; record: TemplateModel | null };

/**
 * The wizard's server-backed, single-select template picker: typing re-queries the backend and only
 * a returned template can be chosen. Selecting one hands the record to the parent, which validates it
 * (a template with mandatory, defaultless fields is blocked there). Reuses the shared Search model,
 * so results are already permission-filtered to SAMPLE_TEMPLATE.
 */
function WizardTemplatePicker({
  setTemplate,
  selectedTemplateId = null,
  selectedTemplateName,
}: {
  /** The chosen template, or null when the user clears the box (which must clear the parent too). */
  setTemplate: (template: TemplateModel | null) => void;
  /** The already-chosen template (if any), so a reopened picker starts pre-filled on it. */
  selectedTemplateId?: number | null;
  selectedTemplateName?: string;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const [search] = React.useState(
    () =>
      new Search({
        factory: new AlwaysNewFactory(),
        // Server does the filtering, so a small page is enough.
        // ponytail: no pager here, add one if template counts get big.
        fetcherParams: { resultType: "SAMPLE_TEMPLATE", pageSize: 25, orderBy: "name", order: "asc" },
        uiConfig: {
          allowedSearchModules: new Set(["TYPE", "OWNER", "SAVEDSEARCHES", "TAG"]),
          allowedTypeFilters: new Set(["SAMPLE_TEMPLATE"]),
          selectionMode: "SINGLE",
        },
      }),
  );

  // The already-selected template, shown pre-filled. It may not be in the current result page, so it
  // is carried as its own option with no record (selecting it again is a no-op; it is already chosen).
  const preselected: TemplateOption | null =
    selectedTemplateId != null
      ? { id: selectedTemplateId, name: selectedTemplateName ?? "", globalId: "", record: null }
      : null;
  const [value, setValue] = React.useState<TemplateOption | null>(preselected);
  const [inputValue, setInputValue] = React.useState(preselected?.name ?? "");

  // Load an initial list as soon as the picker opens (empty query), so the dropdown is never empty.
  React.useEffect(() => {
    void search.fetcher.performInitialSearch(null);
  }, [search]);

  // Debounced. Below MIN_SEARCH_CHARS, the initial (unfiltered) list is shown instead of sending a
  // query that would 422.
  const runSearch = React.useMemo(
    () =>
      debounce((query: string) => {
        const trimmed = query.trim();
        if (trimmed.length >= MIN_SEARCH_CHARS) {
          void search.fetcher.performInitialSearch({ query: trimmed, resultType: "SAMPLE_TEMPLATE" });
        } else {
          void search.fetcher.performInitialSearch(null);
        }
      }, SEARCH_DEBOUNCE_MS),
    [search],
  );
  React.useEffect(() => () => runSearch.cancel(), [runSearch]);

  const results: Array<TemplateOption> = search.results.map((record) => ({
    id: Number(record.id),
    name: record.name,
    globalId: record.globalId ?? "",
    record: record as TemplateModel,
  }));
  const options: Array<TemplateOption> =
    preselected && !results.some((option) => option.id === preselected.id) ? [preselected, ...results] : results;

  return (
    <Autocomplete
      options={options}
      value={value}
      inputValue={inputValue}
      loading={search.fetcher.loading}
      isOptionEqualToValue={(option, selected) => option.id === selected.id}
      getOptionLabel={(option) => option.name}
      // The server already filtered; return the options unchanged rather than filtering again client-side.
      filterOptions={(opts) => opts}
      onChange={(_event, next) => {
        setValue(next);
        // Must call setTemplate(null) on clear, or the wizard keeps the previous template id even
        // though the box looks empty. Re-picking the pre-filled placeholder (no record) is the one
        // no-op: it is already the parent's selection.
        if (next === null) setTemplate(null);
        else if (next.record) setTemplate(next.record);
      }}
      onInputChange={(_event, next, reason) => {
        setInputValue(next);
        // Re-query on typing and on clearing (which returns to the initial list); ignore "reset",
        // which fires when a selection syncs the input and must not trigger a search.
        if (reason === "input" || reason === "clear") runSearch(next);
      }}
      renderOption={(props, option) => (
        <li {...props} key={option.id}>
          {/* The GlobalId pill needs the underlying record, so the pre-filled placeholder (record null)
              shows the name only. */}
          <Stack direction="row" spacing={1} sx={{ alignItems: "center", justifyContent: "space-between", flex: 1 }}>
            <Typography variant="body2" component="span">
              {option.name}
            </Typography>
            {option.record ? <GlobalId record={option.record} /> : null}
          </Stack>
        </li>
      )}
      renderInput={(params) => (
        <TextField {...params} size="small" margin="dense" label={t("operations.template.searchLabel")} />
      )}
      noOptionsText={t("operations.template.noTemplates")}
      loadingText={t("operations.template.loadingTemplates")}
    />
  );
}

export default observer(WizardTemplatePicker);
