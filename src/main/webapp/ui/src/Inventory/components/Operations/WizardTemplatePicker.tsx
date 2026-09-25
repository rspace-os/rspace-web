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
const MIN_SEARCH_CHARS = 2;

/** `record` is null for a pre-filled placeholder: an already-chosen template that is not in the
 *  current result page, so no model for it is to hand. */
type TemplateOption = { id: number; name: string; globalId: string; record: TemplateModel | null };

function WizardTemplatePicker({
  setTemplate,
  selectedTemplateId = null,
  selectedTemplateName,
}: {
  setTemplate: (template: TemplateModel | null) => void;
  selectedTemplateId?: number | null;
  selectedTemplateName?: string;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const [search] = React.useState(
    () =>
      new Search({
        factory: new AlwaysNewFactory(),
        // ponytail: no pager here, add one if template counts get big.
        fetcherParams: { resultType: "SAMPLE_TEMPLATE", pageSize: 25, orderBy: "name", order: "asc" },
        uiConfig: {
          allowedSearchModules: new Set(["TYPE", "OWNER", "SAVEDSEARCHES", "TAG"]),
          allowedTypeFilters: new Set(["SAMPLE_TEMPLATE"]),
          selectionMode: "SINGLE",
        },
      }),
  );

  const preselected: TemplateOption | null =
    selectedTemplateId != null
      ? { id: selectedTemplateId, name: selectedTemplateName ?? "", globalId: "", record: null }
      : null;
  const [value, setValue] = React.useState<TemplateOption | null>(preselected);
  const [inputValue, setInputValue] = React.useState(preselected?.name ?? "");

  React.useEffect(() => {
    void search.fetcher.performInitialSearch(null);
  }, [search]);

  // Below MIN_SEARCH_CHARS the unfiltered list is shown, rather than sending a query that would 422.
  // The fetcher keeps the last query it was given and performInitialSearch(null) re-sends it, so the
  // stored query is cleared first or the old results would stay on screen under an empty box.
  const runSearch = React.useMemo(
    () =>
      debounce((query: string) => {
        const trimmed = query.trim();
        if (trimmed.length >= MIN_SEARCH_CHARS) {
          void search.fetcher.performInitialSearch({ query: trimmed, resultType: "SAMPLE_TEMPLATE" });
        } else {
          search.fetcher.setAttributes({ query: "" });
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
        // though the box looks empty.
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
