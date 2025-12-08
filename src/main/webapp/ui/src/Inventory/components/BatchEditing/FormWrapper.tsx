import Box from "@mui/material/Box";
import type { ReactNode } from "react";
import { HeadingContext } from "../../../components/DynamicHeadingLevel";
import type { AllowedFormTypes } from "../../../stores/contexts/FormSections";
import type { Editable } from "../../../stores/definitions/Editable";
import CommonEditActions from "../CommonEditActions";
import Toolbar from "../Toolbar/Toolbar";

type FormWrapperArgs = {
    titleText: string;
    recordType: AllowedFormTypes;
    children: ReactNode;
    editableObject: Editable;
};

/**
 * A wrapper for batch editing forms that provides the floating toolbar and the
 * footer with the save/cancel buttons.
 */
export default function FormWrapper({ titleText, recordType, children, editableObject }: FormWrapperArgs): ReactNode {
    return (
        <>
            <Toolbar title={titleText} recordType={recordType} batch />
            <Box sx={{ minHeight: 0, overflowY: "auto" }}>
                <HeadingContext level={3}>{children}</HeadingContext>
                <CommonEditActions editableObject={editableObject} />
            </Box>
        </>
    );
}
