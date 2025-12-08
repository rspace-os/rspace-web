import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import { observer } from "mobx-react-lite";
import { type ReactNode, useEffect, useState } from "react";
import AlwaysNewFactory from "../../stores/models/Factory/AlwaysNewFactory";
import type SubSampleModel from "../../stores/models/SubSampleModel";
import { SubSampleCollection } from "../../stores/models/SubSampleModel";
import useStores from "../../stores/use-stores";
import type RsSet from "../../util/set";
import BatchEditingItemsTable from "../components/BatchEditing/BatchEditingItemsTable";
import FormWrapper from "../components/BatchEditing/FormWrapper";
import BarcodesField from "../components/Fields/Barcodes/FormField";
import BatchName from "../components/Fields/BatchName";
import Description from "../components/Fields/Description";
import Image from "../components/Fields/Image";
import Tags from "../components/Fields/Tags";
import StepperPanel from "../components/Stepper/StepperPanel";
import { setFormSectionError, useFormSectionError } from "../components/Stepper/StepperPanelHeader";
import Quantity from "./Fields/Quantity";

type OverviewSectionArgs = {
    setOfSubSamples: SubSampleCollection;
    recordsCount: number;
};

function OverviewSection({ setOfSubSamples, recordsCount }: OverviewSectionArgs): ReactNode {
    const formSectionError = useFormSectionError({
        editing: true,
        globalId: null,
    });

    return (
        <StepperPanel
            icon="subsample"
            title="Overview"
            formSectionError={formSectionError}
            sectionName="overview"
            recordType="subSample"
        >
            <Image fieldOwner={setOfSubSamples} alt={`What the ${setOfSubSamples.size} subsamples look like`} />
            {setOfSubSamples.isFieldEditable("image") && (
                <Box mt={1}>
                    <Alert severity="info">
                        Please note, on slower network connections uploading large images may trigger an error.
                    </Alert>
                </Box>
            )}
            <BatchName
                fieldOwner={setOfSubSamples}
                allowAlphabeticalSuffix={recordsCount <= 26}
                onErrorStateChange={(value) => setFormSectionError(formSectionError, "name", value)}
            />
        </StepperPanel>
    );
}

type DetailsSectionArgs = {
    setOfSubSamples: SubSampleCollection;
};

function DetailsSection({ setOfSubSamples }: DetailsSectionArgs): ReactNode {
    const formSectionError = useFormSectionError({
        editing: true,
        globalId: null,
    });

    return (
        <StepperPanel
            icon="subsample"
            title="Details"
            formSectionError={formSectionError}
            sectionName="details"
            recordType="subSample"
        >
            {setOfSubSamples.sameQuantityUnits ? (
                <Quantity
                    fieldOwner={setOfSubSamples}
                    quantityCategory={setOfSubSamples.quantityCategory}
                    onErrorStateChange={(value) => setFormSectionError(formSectionError, "quantity", value)}
                />
            ) : (
                <Alert severity="warning">
                    Quantity cannot be edited as the subsamples use a variety of different units.
                </Alert>
            )}
            <Description
                fieldOwner={setOfSubSamples}
                onErrorStateChange={(e) => setFormSectionError(formSectionError, "description", e)}
            />
            <Tags fieldOwner={setOfSubSamples} />
        </StepperPanel>
    );
}

type BatchFormArgs = {
    records: RsSet<SubSampleModel>;
};

function BatchForm({ records }: BatchFormArgs): ReactNode {
    const { searchStore } = useStores();

    const [setOfSubSamples, setSetOfSubSamples] = useState(new SubSampleCollection(records));
    useEffect(() => {
        setSetOfSubSamples(new SubSampleCollection(records));
    }, [records]);

    return (
        <FormWrapper
            recordType="subSample"
            titleText={`Batch editing ${records.size} subsamples`}
            editableObject={searchStore.search.batchEditableInstance}
        >
            <StepperPanel icon="subsample" title="Information" sectionName="information" recordType="subSample">
                <BatchEditingItemsTable records={records} label="Subsamples being edited" />
            </StepperPanel>
            <OverviewSection setOfSubSamples={setOfSubSamples} recordsCount={records.size} />
            <DetailsSection setOfSubSamples={setOfSubSamples} />
            <StepperPanel icon="subsample" title="Barcodes" sectionName="barcodes" recordType="subSample">
                <BarcodesField fieldOwner={setOfSubSamples} factory={new AlwaysNewFactory()} />
            </StepperPanel>
        </FormWrapper>
    );
}

export default observer(BatchForm);
