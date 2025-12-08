import { faSpinner } from "@fortawesome/free-solid-svg-icons/faSpinner";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import DownloadIcon from "@mui/icons-material/GetApp";
import InsertPhotoIcon from "@mui/icons-material/InsertPhoto";
import Backdrop from "@mui/material/Backdrop";
import CircularProgress from "@mui/material/CircularProgress";
import Grid from "@mui/material/Grid";
import TableCell from "@mui/material/TableCell";
import TableRow from "@mui/material/TableRow";
import { observer } from "mobx-react-lite";
import React, { type ReactNode, useId, useState } from "react";
import ChemistryIcon from "../../../../assets/graphics/ChemistryIcon";
import IconButtonWithTooltip from "../../../../components/IconButtonWithTooltip";
import { mkAlert } from "../../../../stores/contexts/Alert";
import type { Attachment } from "../../../../stores/definitions/Attachment";
import type { HasEditableFields } from "../../../../stores/definitions/Editable";
import useStores from "../../../../stores/use-stores";
import { capImageAt1MB } from "../../../../util/images";
import type { BlobUrl } from "../../../../util/types";
import { doNotAwait } from "../../../../util/Util";
import DeleteButton from "../../DeleteButton";
import NameWithBadge from "../../NameWithBadge";
import Preview from "./PreviewAction";

const KetcherDialog = React.lazy(() => import("../../../../components/Ketcher/KetcherDialog"));

const ChemicalPreview = observer(({ attachment }: { attachment: Attachment }) => {
    const { loadingString, chemicalString, isChemicalFile, chemistrySupported } = attachment;

    const [showPreview, setShowPreview] = useState(true);

    const handleClick = () => {
        attachment
            .createChemicalPreview()
            .then(() => {
                setShowPreview(true);
            })
            .catch(() => {});
    };

    return (
        <>
            <IconButtonWithTooltip
                title={
                    chemicalString
                        ? ""
                        : loadingString
                          ? "Loading file"
                          : chemistrySupported
                            ? "Preview file as 3D structure"
                            : isChemicalFile && !attachment.id
                              ? "Save first to enable 3D preview"
                              : "3D Preview is not supported for this file type"
                }
                size="small"
                color="primary"
                onClick={handleClick}
                disabled={loadingString || !chemistrySupported}
                icon={loadingString ? <FontAwesomeIcon icon={faSpinner} spin size="lg" /> : <ChemistryIcon />}
            />
            {!loadingString && Boolean(chemicalString) && showPreview && (
                <React.Suspense
                    fallback={
                        <Backdrop
                            open
                            sx={{
                                color: "#fff",
                                zIndex: (theme) => theme.zIndex.drawer + 1,
                            }}
                        >
                            <CircularProgress color="inherit" />
                        </Backdrop>
                    }
                >
                    <KetcherDialog
                        isOpen
                        handleInsert={() => {}}
                        title={"View Chemical (Read-only)"}
                        existingChem={attachment.chemicalString}
                        handleClose={() => {
                            setShowPreview(false);
                        }}
                        readOnly={true}
                    />
                </React.Suspense>
            )}
        </>
    );
});

const Download = ({ attachment }: { attachment: Attachment }) => (
    <IconButtonWithTooltip
        title="Download"
        size="small"
        color="primary"
        onClick={() => {
            void attachment.download();
        }}
        icon={<DownloadIcon />}
    />
);

const SetAsPreviewImage = <
    Fields extends {
        image: BlobUrl | null;
        newBase64Image: string | null;
    },
    FieldOwner extends HasEditableFields<Fields>,
>({
    attachment,
    disabled,
    fieldOwner,
}: {
    attachment: Attachment;
    disabled: boolean;
    fieldOwner?: FieldOwner;
}): ReactNode => {
    const { uiStore } = useStores();
    const canvasId = useId();

    const storeImage = async (dataURL: string | null, file: Blob | null) => {
        if (!fieldOwner) throw new Error("The preview image cannot be set as the item is not available.");
        if (!dataURL || !file) throw new Error("Unable to set attachment as preview image.");
        const scaledImage = await capImageAt1MB(file, dataURL, canvasId);
        fieldOwner.setFieldsDirty({
            image: scaledImage,
            newBase64Image: scaledImage,
        });
        uiStore.addAlert(
            mkAlert({
                message: `Setting ${attachment.name} as preview image. Please Save the item to confirm.`,
                variant: "notice",
                isInfinite: false,
            }),
        );
    };

    const setAsPreviewImage = async () => {
        try {
            await attachment.setImageLink();
            await storeImage(attachment.imageLink, await attachment.getFile());
        } catch (e) {
            uiStore.addAlert(
                mkAlert({
                    title: "Could not fetch image",
                    message: (e as Error).message,
                    variant: "error",
                }),
            );
        }
    };
    return (
        <>
            <IconButtonWithTooltip
                title={
                    disabled && !attachment.previewSupported
                        ? "Save first to enable setting this file as the item's Preview Image."
                        : disabled
                          ? "First press Edit to set as Preview Image."
                          : "Set as Preview Image"
                }
                size="small"
                color="primary"
                onClick={doNotAwait(setAsPreviewImage)}
                icon={<InsertPhotoIcon />}
                disabled={disabled}
            />
            <canvas id={canvasId} style={{ display: "none" }}></canvas>
        </>
    );
};

function AttachmentTableRow<
    Fields extends {
        image: BlobUrl | null;
        newBase64Image: string | null;
    },
    FieldOwner extends HasEditableFields<Fields>,
>({
    attachment,
    fieldOwner,
    editable,
    chemistryProvider: _ = "",
}: {
    attachment: Attachment;
    fieldOwner?: FieldOwner;
    editable: boolean;
    chemistryProvider: string;
}): ReactNode {
    return (
        <TableRow>
            <TableCell>
                <NameWithBadge record={attachment} />
            </TableCell>
            <TableCell width={1}>
                <Grid container direction="row" spacing={1} wrap="nowrap">
                    <Grid item>
                        <Preview attachment={attachment} />
                    </Grid>
                    <Grid item>
                        <ChemicalPreview attachment={attachment} />
                    </Grid>
                    <Grid item>
                        <SetAsPreviewImage
                            attachment={attachment}
                            disabled={!editable || !attachment.previewSupported}
                            fieldOwner={fieldOwner}
                        />
                    </Grid>
                    <Grid item>
                        <Download attachment={attachment} />
                    </Grid>
                    <Grid item>
                        <DeleteButton
                            onClick={() => {
                                attachment.remove();
                            }}
                            disabled={!editable}
                            tooltipAfterClicked="Attachment will be deleted once this item is saved."
                            tooltipBeforeClicked="Remove"
                            tooltipWhenDisabled="First press Edit to remove this attachment."
                        />
                    </Grid>
                </Grid>
            </TableCell>
        </TableRow>
    );
}

export default observer(AttachmentTableRow);
