import FormControlLabel from "@mui/material/FormControlLabel";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import AlwaysNewWindowNavigationContext from "@/components/AlwaysNewWindowNavigationContext";
import GlobalId from "@/components/GlobalId";
import type { InventoryRecord } from "@/stores/definitions/InventoryRecord";
import AlwaysNewFactory from "@/stores/models/Factory/AlwaysNewFactory";
import Search from "@/stores/models/Search";
import TemplateModel from "@/stores/models/TemplateModel";

/**
 * A deliberately minimal template picker used ONLY by the operation wizard: a search box plus a
 * radio list of the allowed templates, each showing its name and its global id as the standard
 * Inventory pill (a clickable link, with the template record-type icon, via the shared GlobalId
 * component). It reuses the shared Search model (so it fetches the same permission-filtered
 * SAMPLE_TEMPLATE results and searches the same way) but renders its own stripped-down UI, so it
 * does not touch the shared TemplatePicker/InventoryPicker used elsewhere in Inventory.
 *
 * The list is wrapped in AlwaysNewWindowNavigationContext so clicking a template's id-pill opens
 * that template in a new window rather than navigating away from (and losing) the wizard.
 */
function WizardTemplatePicker({ setTemplate }: { setTemplate: (template: TemplateModel) => void }): React.ReactNode {
  const { t } = useTranslation("inventory");
  const [search] = React.useState(
    () =>
      new Search({
        factory: new AlwaysNewFactory(),
        // pageSize is generous so most template libraries fit without paging; the search box
        // narrows anything larger. ponytail: no pager here, add one if template counts get big.
        fetcherParams: { resultType: "SAMPLE_TEMPLATE", pageSize: 25, orderBy: "name", order: "asc" },
        uiConfig: {
          allowedSearchModules: new Set(["TYPE", "OWNER", "SAVEDSEARCHES", "TAG"]),
          allowedTypeFilters: new Set(["SAMPLE_TEMPLATE"]),
          selectionMode: "SINGLE",
        },
      }),
  );
  const [query, setQuery] = React.useState("");
  const [selectedGlobalId, setSelectedGlobalId] = React.useState<string | null>(null);

  React.useEffect(() => {
    void search.fetcher.performInitialSearch(null);
  }, [search]);

  const runSearch = () => {
    void search.fetcher.performInitialSearch({ query, resultType: "SAMPLE_TEMPLATE" });
  };

  const select = (record: InventoryRecord) => {
    if (!(record instanceof TemplateModel)) return;
    setSelectedGlobalId(record.globalId ?? null);
    setTemplate(record);
  };

  return (
    <Stack spacing={1}>
      <TextField
        size="small"
        margin="dense"
        value={query}
        label={t("operations.template.searchLabel")}
        onChange={(e) => setQuery(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter") {
            e.preventDefault();
            runSearch();
          }
        }}
      />
      {search.fetcher.loading ? (
        <Typography variant="body2">{t("operations.template.loadingTemplates")}</Typography>
      ) : search.results.length === 0 ? (
        <Typography variant="body2">{t("operations.template.noTemplates")}</Typography>
      ) : (
        <AlwaysNewWindowNavigationContext>
          <RadioGroup value={selectedGlobalId ?? ""}>
            {search.results.map((record) => (
              <FormControlLabel
                key={record.globalId}
                value={record.globalId ?? ""}
                control={<Radio />}
                onChange={() => select(record)}
                label={
                  <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
                    <Typography variant="body2" component="span">
                      {record.name}
                    </Typography>
                    <GlobalId record={record} size="small" />
                  </Stack>
                }
              />
            ))}
          </RadioGroup>
        </AlwaysNewWindowNavigationContext>
      )}
    </Stack>
  );
}

export default observer(WizardTemplatePicker);
