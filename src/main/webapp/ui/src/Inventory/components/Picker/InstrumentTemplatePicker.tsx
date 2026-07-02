import { observer } from "mobx-react-lite";
import React, { type ReactNode, useEffect, useState } from "react";
import AlwaysNewWindowNavigationContext from "../../../components/AlwaysNewWindowNavigationContext";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import AlwaysNewFactory from "../../../stores/models/Factory/AlwaysNewFactory";
import type InstrumentModel from "../../../stores/models/InstrumentModel";
import InstrumentTemplateModel from "../../../stores/models/InstrumentTemplateModel";
import Search from "../../../stores/models/Search";
import InventoryPicker from "./Picker";

type InstrumentTemplatePickerArgs = {
  setTemplate: (template: InstrumentTemplateModel) => void;
  disabled?: boolean;
  instrument?: InstrumentModel;
};

function InstrumentTemplatePicker({ setTemplate, disabled, instrument }: InstrumentTemplatePickerArgs): ReactNode {
  const [search] = useState(
    new Search({
      factory: new AlwaysNewFactory(),
      fetcherParams: {
        resultType: "INSTRUMENT_TEMPLATE",
        pageSize: 5,
        orderBy: "name",
        order: "asc",
      },
      uiConfig: {
        allowedSearchModules: new Set(["TYPE", "OWNER", "SAVEDSEARCHES", "TAG"]),
        allowedTypeFilters: new Set(["INSTRUMENT_TEMPLATE"]),
        selectionMode: "SINGLE",
      },
    }),
  );

  useEffect(() => {
    if (typeof instrument !== "undefined" && search.activeResult !== instrument.template) {
      search.setActiveResult(instrument.template, { defaultToFirstResult: false });
    }
  }, [instrument?.template]);

  useEffect(() => {
    if (!disabled) void search.fetcher.performInitialSearch(null);
  }, []);

  const handleOnAddition = React.useCallback(
    ([t]: Array<InventoryRecord>) => {
      if (!(t instanceof InstrumentTemplateModel)) {
        throw new Error("Only InstrumentTemplate can be chosen");
      }
      setTemplate(t);
    },
    [setTemplate],
  );

  return (
    <AlwaysNewWindowNavigationContext>
      <InventoryPicker search={search} paddingless onAddition={handleOnAddition} />
    </AlwaysNewWindowNavigationContext>
  );
}

export default observer(InstrumentTemplatePicker);
