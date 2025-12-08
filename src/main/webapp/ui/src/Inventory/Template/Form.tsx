import { observer } from "mobx-react-lite";
import type { ReactNode } from "react";
import type { Person } from "../../stores/definitions/Person";
import TemplateModel from "../../stores/models/TemplateModel";
import useStores from "../../stores/use-stores";
import AccessPermissions from "../components/Fields/AccessPermissions";
import Description from "../components/Fields/Description";
import ImageField from "../components/Fields/Image";
import NameField from "../components/Fields/Name";
import OwnerField from "../components/Fields/Owner";
import Tags from "../components/Fields/Tags";
import LimitedAccessAlert from "../components/LimitedAccessAlert";
import Stepper from "../components/Stepper/Stepper";
import StepperPanel from "../components/Stepper/StepperPanel";
import { setFormSectionError, useFormSectionError } from "../components/Stepper/StepperPanelHeader";
import Expiry from "../Sample/Fields/Expiry";
import Source from "../Sample/Fields/Source";
import StorageTemperature from "../Sample/Fields/StorageTemperature";
import SubSampleAlias from "./Fields/Alias";
import CustomFields from "./Fields/CustomFields";
import QuantityUnits from "./Fields/QuantityUnits";
import SamplesList from "./Fields/SamplesList";
import VersionInfo from "./Fields/VersionInfo";

const OverviewSection = observer(({ activeResult }: { activeResult: TemplateModel }) => {
    const formSectionError = useFormSectionError({
        editing: activeResult.editing,
        globalId: activeResult.globalId,
    });

    return (
        <StepperPanel
            title="Overview"
            sectionName="overview"
            formSectionError={formSectionError}
            recordType="sampleTemplate"
        >
            <NameField
                fieldOwner={activeResult}
                record={activeResult}
                onErrorStateChange={(e) => setFormSectionError(formSectionError, "name", e)}
            />
            <OwnerField fieldOwner={activeResult} />
            {activeResult.readAccessLevel !== "public" && (
                <ImageField
                    fieldOwner={activeResult}
                    alt="A visual representation of the samples that are to be created from this template"
                />
            )}
        </StepperPanel>
    );
});

const DetailsSection = observer(({ activeResult }: { activeResult: TemplateModel }) => {
    const formSectionError = useFormSectionError({
        editing: activeResult.editing,
        globalId: activeResult.globalId,
    });

    return (
        <StepperPanel
            title="Details"
            sectionName="details"
            formSectionError={formSectionError}
            recordType="sampleTemplate"
        >
            <Expiry
                fieldOwner={activeResult}
                onErrorStateChange={(value) => setFormSectionError(formSectionError, "expiry", value)}
            />
            <Source fieldOwner={activeResult} />
            <StorageTemperature
                fieldOwner={activeResult}
                onErrorStateChange={(value) => setFormSectionError(formSectionError, "temperature", value)}
            />
            <SubSampleAlias
                fieldOwner={activeResult}
                onErrorStateChange={(value) => setFormSectionError(formSectionError, "alias", value)}
            />
            <QuantityUnits />
            <Description
                fieldOwner={activeResult}
                onErrorStateChange={(e) => setFormSectionError(formSectionError, "description", e)}
            />
            <Tags fieldOwner={activeResult} />
        </StepperPanel>
    );
});

const FieldsSection = observer(({ activeResult }: { activeResult: TemplateModel }) => {
    const formSectionError = useFormSectionError({
        editing: activeResult.editing,
        globalId: activeResult.globalId,
    });

    return (
        <StepperPanel
            title="Custom Fields"
            sectionName="customFields"
            formSectionError={formSectionError}
            recordType="sampleTemplate"
        >
            <CustomFields onErrorStateChange={(name, value) => setFormSectionError(formSectionError, name, value)} />
        </StepperPanel>
    );
});

function Form(): ReactNode {
    const {
        searchStore: { activeResult },
    } = useStores();
    if (!activeResult || !(activeResult instanceof TemplateModel)) throw new Error("ActiveResult must be a Template");
    if (!activeResult.owner) throw new Error("Template does not have an owner");
    const owner: Person = activeResult.owner;

    return (
        <Stepper
            stickyAlert={activeResult.historicalVersion ? <VersionInfo template={activeResult} /> : null}
            titleText={activeResult.name}
            resetScrollPosition={activeResult}
            factory={activeResult.factory}
        >
            <LimitedAccessAlert readAccessLevel={activeResult.readAccessLevel} owner={owner} whatLabel="template" />
            <OverviewSection activeResult={activeResult} />
            {activeResult.readAccessLevel !== "public" && <DetailsSection activeResult={activeResult} />}
            {activeResult.readAccessLevel === "full" && (
                <>
                    <StepperPanel title="Access Permissions" sectionName="permissions" recordType="sampleTemplate">
                        <AccessPermissions
                            fieldOwner={activeResult}
                            additionalExplanation="This template will also be accessible to anyone who has access to a sample that has been created from it."
                        />
                    </StepperPanel>
                    <FieldsSection activeResult={activeResult} />
                    {activeResult.state === "preview" ? (
                        <StepperPanel title="Samples" sectionName="samples" recordType="sampleTemplate">
                            <SamplesList />
                        </StepperPanel>
                    ) : null}
                </>
            )}
        </Stepper>
    );
}

export default observer(Form);
