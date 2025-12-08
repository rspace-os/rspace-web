import GetApp from "@mui/icons-material/GetApp";
import { Observer } from "mobx-react-lite";
import React, { forwardRef, useContext, useState } from "react";
import AnalyticsContext from "../../../stores/contexts/Analytics";
import SearchContext from "../../../stores/contexts/Search";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import { match } from "../../../util/Util";
import ExportDialog from "../Export/ExportDialog";
import ContextMenuAction, { type ContextMenuRenderOptions } from "./ContextMenuAction";

type ExportActionArgs = {
    as: ContextMenuRenderOptions;
    closeMenu: () => void;
    disabled: string;
    selectedResults: Array<InventoryRecord>;
};

const ExportAction = forwardRef<React.ElementRef<typeof ContextMenuAction>, ExportActionArgs>(
    ({ as, closeMenu, disabled, selectedResults }: ExportActionArgs, ref) => {
        const { search } = useContext(SearchContext);
        const [openExportDialog, setOpenExportDialog] = useState(false);
        const { trackEvent } = React.useContext(AnalyticsContext);

        const handleOpen = () => {
            trackEvent("user:open:selectionExportDialog:Inventory");
            setOpenExportDialog(true);
        };

        const disabledHelp = match<void, string>([
            [() => disabled !== "", disabled],
            [
                () => selectedResults.some((r) => !r.canRead),
                `You do not have permission to export ${selectedResults.length > 1 ? "these items" : "this item"}.`,
            ],
            [() => true, ""],
        ])();

        return (
            <Observer>
                {() => (
                    <ContextMenuAction
                        onClick={handleOpen}
                        icon={<GetApp />}
                        label="Export"
                        disabledHelp={disabledHelp}
                        as={as}
                        ref={ref}
                    >
                        {openExportDialog && (
                            <ExportDialog
                                openExportDialog={openExportDialog}
                                setOpenExportDialog={setOpenExportDialog}
                                onExport={(exportOptions) => {
                                    void search.exportRecords(exportOptions, selectedResults);
                                }}
                                exportType="contextMenu"
                                closeMenu={closeMenu}
                                selectedResults={selectedResults}
                            />
                        )}
                    </ContextMenuAction>
                )}
            </Observer>
        );
    },
);

ExportAction.displayName = "ExportAction";
export default ExportAction;
