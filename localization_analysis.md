# RSpace Localization Infrastructure Analysis

## Message Source Configuration

The application uses Spring's `ReloadableResourceBundleMessageSource` for handling localization. The configuration is defined in `/src/main/resources/applicationContext-resources.xml`:

```xml
<bean id="messageSource" class="org.springframework.context.support.ReloadableResourceBundleMessageSource">
    <property name="basenames">
        <list>
            <value>classpath:bundles/dashboard/dashboard</value>
            <value>classpath:bundles/ApplicationResources</value>
            <value>classpath:bundles/workspace/editor</value>
            <value>classpath:bundles/gallery/gallery</value>
            <value>classpath:bundles/gallery/netfiles</value>
            <value>classpath:bundles/groups/groups</value>
            <value>classpath:bundles/public/public</value>
            <value>classpath:bundles/system/system</value>
            <value>classpath:bundles/system/community</value>
            <value>classpath:bundles/workspace/workspace</value>
            <value>classpath:bundles/admin/admin</value>
            <value>classpath:bundles/admin/community</value>
            <value>classpath:bundles/apps/apps</value>
            <value>classpath:bundles/inventory/inventory</value>
        </list>
    </property>
    <property name="useCodeAsDefaultMessage" value="true" />
</bean>
```

### Key Configuration Details:

1. **Message Source Type**: `ReloadableResourceBundleMessageSource` - allows for reloading of message bundles without restarting the application
2. **Resource Bundle Organization**: Messages are organized into logical modules (dashboard, workspace, gallery, etc.)
3. **Default Message Behavior**: `useCodeAsDefaultMessage` is set to `true`, meaning if a message code is not found, the code itself will be used as the message
4. **Inventory Module**: There's already a dedicated bundle for inventory at `classpath:bundles/inventory/inventory`

## Existing Resource Bundles

The application has the following resource bundle structure:

```
bundles/
├── admin/
│   ├── admin.properties
│   └── community.properties
├── apps/
│   └── apps.properties
├── dashboard/
│   └── dashboard.properties
├── gallery/
│   ├── gallery.properties
│   └── netfiles.properties
├── groups/
│   └── groups.properties
├── inventory/
│   └── inventory.properties  ← Target for new messages
├── public/
│   └── public.properties
├── system/
│   ├── community.properties
│   └── system.properties
└── workspace/
    ├── editor.properties
    └── workspace.properties
├── ApplicationResources.properties  ← Main fallback bundle
```

## Inventory Module Analysis

The inventory module already contains several validation error messages. From `/src/main/resources/bundles/inventory/inventory.properties`:

```properties
# inventory API error messages
container.deletion.failure.not.empty=Container {0} is not empty and cannot be deleted
move.failure.no.target.location.for.grid.image.container=When moving to image/grid type container request has to specify target location
# ... more messages
errors.inventory.expiryDate.invalid=Expiry date {0} must be in the future.
errors.inventory.template.invalid.unitId=Unit id was {0} must be the id of a unit (see /units)
# ... more messages
```

## Validation Message Handling

Looking at the main `ApplicationResources.properties`, I can see standard validation error messages:

```properties
# -- validator errors --
errors.invalid={0} is invalid.
errors.maxlength={0} cannot be greater than {1} characters.
errors.minlength={0} cannot be less than {1} characters.
# ... more generic validation messages

# -- inventory validator errors --
errors.inventory.temperature.unitsNotComparable=Temperature units are not mutually comparable.
# ... more inventory-specific validation messages
```

## Implementation Approach

To add localization support for new inventory features, we should:

1. Add new messages to the existing `bundles/inventory/inventory.properties` file
2. Follow the existing naming conventions (e.g., `errors.inventory.*`)
3. Use parameterized messages with `{0}`, `{1}`, etc. for dynamic values
4. Ensure messages are organized logically within the file
5. Consider adding translations by creating language-specific files (e.g., `inventory_es.properties`)

The infrastructure is already in place and properly configured, so we just need to leverage it by adding our new messages to the appropriate resource bundle files.