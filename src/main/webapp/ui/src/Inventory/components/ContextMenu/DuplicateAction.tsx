import AddToPhotosIcon from "@mui/icons-material/AddToPhotos";
import { Observer } from "mobx-react-lite";
import type React from "react";
import { forwardRef, useContext } from "react";
import SearchContext from "../../../stores/contexts/Search";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import SubSampleModel from "../../../stores/models/SubSampleModel";
import { match } from "../../../util/Util";
import ContextMenuAction, { type ContextMenuRenderOptions } from "./ContextMenuAction";

type DuplicateActionArgs = {
    as: ContextMenuRenderOptions;
    disabled: string;
    selectedResults: Array<InventoryRecord>;
    closeMenu: () => void;
};

const DuplicateAction = forwardRef<React.ElementRef<typeof ContextMenuAction>, DuplicateActionArgs>(
    ({ as, disabled, selectedResults, closeMenu }: DuplicateActionArgs, ref) => {
        const { search } = useContext(SearchContext);

        const disabledHelp = match<void, string>([
            [() => disabled !== "", disabled],
            [
                () => selectedResults.some((r) => r instanceof SubSampleModel && !r.canEdit),
                `You do not have permission to duplicate ${selectedResults.length > 1 ? "these items" : "this item"}.`,
            ],
            [
                () => selectedResults.some((r) => !r.canRead),
                `You do not have permission to duplicate ${selectedResults.length > 1 ? "these items" : "this item"}.`,
            ],
            [() => true, ""],
        ])();

        return (
            <Observer>
                {() => (
                    <ContextMenuAction
                        onClick={() => {
                            void search.duplicateRecords(selectedResults);
                            closeMenu();
                        }}
                        icon={<AddToPhotosIcon />}
                        label="Duplicate"
                        disabledHelp={disabledHelp}
                        as={as}
                        ref={ref}
                    />
                )}
            </Observer>
        );
    },
);

DuplicateAction.displayName = "DuplicateAction";
export default DuplicateAction;
