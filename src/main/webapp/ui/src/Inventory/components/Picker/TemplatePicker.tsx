import React, { type ReactNode, useState, useEffect } from "react";
import { observer, useLocalObservable } from "mobx-react-lite";
import TemplateModel from "../../../stores/models/TemplateModel";
import Search from "../../../stores/models/Search";
import AlwaysNewFactory from "../../../stores/models/Factory/AlwaysNewFactory";
import InventoryPicker from "./Picker";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import AlwaysNewWindowNavigationContext from "../../../components/AlwaysNewWindowNavigationContext";
import { Sample } from "@/stores/definitions/Sample";

type TemplatePickerArgs = {
  setTemplate: (template: TemplateModel) => void;
  disabled?: boolean;
  sample?: Sample;
};

function TemplatePicker({
  setTemplate,
  disabled,
  sample,
}: TemplatePickerArgs): ReactNode {
  const search = useLocalObservable(
    () =>
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
        },
      }),
  );

  /*
   * If some other code changes the sample's template, we want to update
   * the active result in the search so that the picker reflects the current
   * template. This is useful when the picker is used in a form where the
   * sample's template may change based on other actions.
   */
  useEffect(() => {
    if (
      typeof sample !== "undefined" &&
      search.activeResult !== sample.template
    ) {
      search.setActiveResult(sample.template, { defaultToFirstResult: false });
    }
  }, [sample?.template]);

  useEffect(() => {
    if (!disabled) void search.fetcher.performInitialSearch(null);
  }, []);

  const handleOnAddition = React.useCallback(
    ([t]: Array<InventoryRecord>) => {
      if (!(t instanceof TemplateModel)) {
        /*
         * This shouldn't happen because the Search passed to the Picker should
         * only allow Templates (see allowedTypeFilters above), but if it does
         * then we want to error
         */
        throw new Error("Only Template can be chosen");
      }
      setTemplate(t);
    },
    [setTemplate],
  );

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
