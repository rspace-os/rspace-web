import React, { type ReactNode, useState, useEffect } from "react";
import { observer } from "mobx-react-lite";
import TemplateModel from "../../../stores/models/TemplateModel";
import Search from "../../../stores/models/Search";
import AlwaysNewFactory from "../../../stores/models/Factory/AlwaysNewFactory";
import InventoryPicker from "./Picker";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import AlwaysNewWindowNavigationContext from "../../../components/AlwaysNewWindowNavigationContext";

type TemplatePickerArgs = {
  setTemplate: (template: TemplateModel) => void;
  disabled?: boolean;
};

function TemplatePicker({
  setTemplate,
  disabled,
}: TemplatePickerArgs): ReactNode {
  const [search] = useState(
    new Search({
      factory: new AlwaysNewFactory(),
      fetcherParams: {
        resultType: "TEMPLATE",
        pageSize: 5,
        orderBy: "name",
        order: "asc",
      },
      uiConfig: {
        allowedSearchModules: new Set([
          "TYPE",
          "OWNER",
          "SAVEDSEARCHES",
          "TAG",
        ]),
        allowedTypeFilters: new Set(["TEMPLATE"]),
        selectionMode: "SINGLE",
        /*
         * Don't highlight the selected template as whilst the activeResult is
         * used by Picker to identify the selected template this is an
         * implementation detail; the activeResult is not shown to the user and
         * so highlighting it is likely to confuse them when it doesn't match
         * the new sample's template due to setTemplate being called elsewhere.
         */
        highlightActiveResult: false,
      },
    })
  );

  useEffect(() => {
    if (!disabled) void search.fetcher.performInitialSearch(null);
  }, []);

  const handleOnAddition = ([t]: Array<InventoryRecord>) => {
    if (!(t instanceof TemplateModel)) {
      /*
       * This shouldn't happen because the Search passed to the Picker should
       * only allow Templates (see allowedTypeFilters above), but if it does
       * then we want to error
       */
      throw new Error("Only Template can be chosen");
    }
    setTemplate(t);
  };

  return (
    <AlwaysNewWindowNavigationContext>
      <InventoryPicker
        search={search}
        paddingless
        onAddition={handleOnAddition}
      />
    </AlwaysNewWindowNavigationContext>
  );
}

export default observer(TemplatePicker);
