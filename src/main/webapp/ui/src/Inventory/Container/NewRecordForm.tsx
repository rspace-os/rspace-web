import { observer } from "mobx-react-lite";
import React from "react";
import docLinks from "../../assets/DocLinks";
import { inventoryRecordTypeLabels } from "../../stores/definitions/BaseRecord";
import ContainerModel from "../../stores/models/ContainerModel";
import useStores from "../../stores/use-stores";
import AccessPermissions from "../components/Fields/AccessPermissions";
import AttachmentsField from "../components/Fields/Attachments/Attachments";
import BarcodesField from "../components/Fields/Barcodes/FormField";
import Description from "../components/Fields/Description";
import ExtraFields from "../components/Fields/ExtraFields/ExtraFields";
import IdentifiersField from "../components/Fields/Identifiers/Identifiers";
import ContainerImage from "../components/Fields/Image";
import NameField from "../components/Fields/Name";
import Tags from "../components/Fields/Tags";
import Stepper from "../components/Stepper/Stepper";
import StepperPanel from "../components/Stepper/StepperPanel";
import { setFormSectionError, useFormSectionError } from "../components/Stepper/StepperPanelHeader";
import SynchroniseFormSections, { UnsynchroniseFormSections } from "../components/Stepper/SynchroniseFormSections";
import CanStore from "./Fields/CanStore";
import Organization from "./Fields/Organization/Organization";

const OverviewSection = observer(({ activeResult }: { activeResult: ContainerModel }) => {
    const formSectionError = useFormSectionError({
        editing: activeResult.editing,
        globalId: activeResult.globalId,
    });

    /*
     * Name is a required field so it effectively starts in an error state.
     */
    React.useEffect(() => {
        setFormSectionError(formSectionError, "name", true);
    }, [formSectionError]);

    return (
        <StepperPanel
            title="Overview"
            sectionName="overview"
            formSectionError={formSectionError}
            recordType="container"
        >
            <NameField
                fieldOwner={activeResult}
                record={activeResult}
                onErrorStateChange={(e) => setFormSectionError(formSectionError, "name", e)}
            />
            <Organization container={activeResult} />
            <ContainerImage fieldOwner={activeResult} alt="What the new container looks like" />
        </StepperPanel>
    );
});

const DetailsSection = observer(({ activeResult }: { activeResult: ContainerModel }) => {
    const formSectionError = useFormSectionError({
        editing: activeResult.editing,
        globalId: activeResult.globalId,
    });

    return (
        <StepperPanel title="Details" sectionName="details" formSectionError={formSectionError} recordType="container">
            <CanStore
                onErrorStateChange={(e) => setFormSectionError(formSectionError, "canstore", e)}
                container={activeResult}
            />
            <Description
                fieldOwner={activeResult}
                onErrorStateChange={(e) => setFormSectionError(formSectionError, "description", e)}
            />
            <Tags fieldOwner={activeResult} />
        </StepperPanel>
    );
});

const ExtaFieldSection = observer(({ activeResult }: { activeResult: ContainerModel }) => {
    const formSectionError = useFormSectionError({
        editing: activeResult.editing,
        globalId: activeResult.globalId,
    });

    return (
        <StepperPanel
            title="Custom Fields"
            sectionName="customFields"
            formSectionError={formSectionError}
            recordType="container"
        >
            <ExtraFields
                onErrorStateChange={(field, value) => setFormSectionError(formSectionError, field, value)}
                result={activeResult}
            />
        </StepperPanel>
    );
});

export default function NewRecordForm(): React.ReactNode {
    const {
        searchStore: { activeResult },
    } = useStores();
    if (!activeResult || !(activeResult instanceof ContainerModel)) throw new Error("ActiveResult must be a Container");

    return (
        <SynchroniseFormSections>
            <Stepper
                helpLink={{
                    link: docLinks.createContainer,
                    title: "Info on creating new containers.",
                }}
                titleText={`New ${inventoryRecordTypeLabels.container}`}
                resetScrollPosition={Symbol("always reset scroll")}
            >
                <UnsynchroniseFormSections>
                    <OverviewSection activeResult={activeResult} />
                </UnsynchroniseFormSections>
                <DetailsSection activeResult={activeResult} />
                <StepperPanel title="Barcodes" sectionName="barcodes" recordType="container">
                    <BarcodesField
                        fieldOwner={activeResult}
                        factory={activeResult.factory}
                        connectedItem={activeResult}
                    />
                </StepperPanel>
                <StepperPanel title="Identifiers" sectionName="identifiers" recordType="container">
                    <IdentifiersField fieldOwner={activeResult} />
                </StepperPanel>
                <StepperPanel title="Attachments" sectionName="attachments" recordType="container">
                    <AttachmentsField fieldOwner={activeResult} />
                </StepperPanel>
                <StepperPanel title="Access Permissions" sectionName="permissions" recordType="container">
                    <AccessPermissions fieldOwner={activeResult} />
                </StepperPanel>
                <ExtaFieldSection activeResult={activeResult} />
            </Stepper>
        </SynchroniseFormSections>
    );
}
