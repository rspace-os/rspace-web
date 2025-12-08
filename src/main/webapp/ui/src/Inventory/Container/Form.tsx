import { observer } from "mobx-react-lite";
import type { ReactNode } from "react";
import type { Person } from "../../stores/definitions/Person";
import ContainerModel from "../../stores/models/ContainerModel";
import useStores from "../../stores/use-stores";
import AccessPermissions from "../components/Fields/AccessPermissions";
import AttachmentsField from "../components/Fields/Attachments/Attachments";
import BarcodesField from "../components/Fields/Barcodes/FormField";
import DescriptionField from "../components/Fields/Description";
import ExtraFields from "../components/Fields/ExtraFields/ExtraFields";
import IdentifiersField from "../components/Fields/Identifiers/Identifiers";
import ContainerImage from "../components/Fields/Image";
import LocationField from "../components/Fields/Location";
import NameField from "../components/Fields/Name";
import OwnerField from "../components/Fields/Owner";
import TagsField from "../components/Fields/Tags";
import LimitedAccessAlert from "../components/LimitedAccessAlert";
import Stepper from "../components/Stepper/Stepper";
import StepperPanel from "../components/Stepper/StepperPanel";
import { setFormSectionError, useFormSectionError } from "../components/Stepper/StepperPanelHeader";
import ContainerContent from "./Content/Content";
import CanStore from "./Fields/CanStore";
import OrganizationField from "./Fields/Organization/Organization";

const OverviewSection = observer(({ activeResult }: { activeResult: ContainerModel }) => {
    const formSectionError = useFormSectionError({
        editing: activeResult.editing,
        globalId: activeResult.globalId,
    });

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
            <OwnerField fieldOwner={activeResult} />
            {activeResult.readAccessLevel !== "public" && (
                <>
                    <LocationField fieldOwner={activeResult} />
                    <OrganizationField container={activeResult} />
                    <ContainerImage fieldOwner={activeResult} alt={`What ${activeResult.name} looks like`} />
                </>
            )}
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
            <DescriptionField
                fieldOwner={activeResult}
                onErrorStateChange={(e) => setFormSectionError(formSectionError, "description", e)}
            />
            <TagsField fieldOwner={activeResult} />
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

function Form(): ReactNode {
    const {
        searchStore: { activeResult },
    } = useStores();
    if (!activeResult || !(activeResult instanceof ContainerModel)) throw new Error("ActiveResult must be a Container");
    if (!activeResult.owner) throw new Error("Container does not have an owner");
    const owner: Person = activeResult.owner;

    return (
        <Stepper titleText={activeResult.name} resetScrollPosition={activeResult} factory={activeResult.factory}>
            <LimitedAccessAlert readAccessLevel={activeResult.readAccessLevel} owner={owner} whatLabel="container" />
            <OverviewSection activeResult={activeResult} />
            {activeResult.readAccessLevel !== "public" && (
                <>
                    <DetailsSection activeResult={activeResult} />
                    <StepperPanel title="Barcodes" sectionName="barcodes" recordType="container">
                        <BarcodesField
                            fieldOwner={activeResult}
                            factory={activeResult.factory}
                            connectedItem={activeResult}
                        />
                    </StepperPanel>
                </>
            )}
            {activeResult.readAccessLevel === "full" && (
                <>
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
                    {activeResult.state === "preview" ? (
                        <StepperPanel
                            title="Locations and Content"
                            sectionName="locationsAndContent"
                            recordType="container"
                        >
                            <ContainerContent />
                        </StepperPanel>
                    ) : null}
                </>
            )}
        </Stepper>
    );
}

export default observer(Form);
