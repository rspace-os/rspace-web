# Hardcoded Strings That Need Localization

This document summarizes all the hardcoded strings found in JSP files that need to be localized for internationalization support.

## 1. Button Labels

### File: `/WEB-INF/pages/workspace/editor/include/editdeleteButton.jsp`
- "Edit" - Button text for editing a field
- "Delete" - Button text for deleting a field

## 2. Messaging Dialog Content

### File: `/WEB-INF/pages/workspace/editor/include/messagingDialogs.jsp`
- "Send a message to {{label}}" - Title of messaging dialog
- "Channel" - Label for channel selection
- "Message" - Label for message input
- "Check my document!" - Placeholder text for message textarea
- "The message will include a link to the current document. Only RSpace users with access to the document will be able to use the link." - Information text for single document sharing
- "The message will include links to selected documents. Only RSpace users with access to these documents will be able to use the links." - Information text for multiple document sharing

## 3. Template Publishing Dialog Content

### File: `/WEB-INF/pages/workspace/editor/include/templatePublishShareDlg.jsp`
- "Configure access to your form by groups that you belong to, or by all RSpace users. If you don't choose any options, the form will remain private to you." - Instructions for template publishing

## 4. Implementation Steps

For each of these hardcoded strings, the following steps need to be taken:

### Step 1: Add Message Keys to Properties Files
Add entries to the appropriate resource bundle file (likely `workspace/editor.properties`):

```properties
# Button labels
button.edit=Edit
button.delete=Delete

# Messaging dialog
messaging.dialog.title=Send a message to {0}
messaging.dialog.channel.label=Channel
messaging.dialog.message.label=Message
messaging.dialog.message.placeholder=Check my document!
messaging.dialog.single.doc.info=The message will include a link to the current document. Only RSpace users with access to the document will be able to use the link.
messaging.dialog.multiple.docs.info=The message will include links to selected documents. Only RSpace users with access to these documents will be able to use the links.

# Template publishing
template.publish.share.info=Configure access to your form by groups that you belong to, or by all RSpace users. If you don't choose any options, the form will remain private to you.
```

### Step 2: Replace Hardcoded Strings in JSP Files
Replace each hardcoded string with the appropriate Spring message tag:

#### Before:
```html
<button class="editButton btn btn-default" onclick="editField(${field.id})">Edit</button>
```

#### After:
```html
<button class="editButton btn btn-default" onclick="editField(${field.id})">
    <spring:message code="button.edit"/>
</button>
```

### Step 3: Add Translations
Create language-specific properties files with translated content:

#### Spanish (`editor_es.properties`):
```properties
button.edit=Editar
button.delete=Eliminar
messaging.dialog.title=Enviar mensaje a {0}
messaging.dialog.channel.label=Canal
messaging.dialog.message.label=Mensaje
messaging.dialog.message.placeholder=¡Mira mi documento!
messaging.dialog.single.doc.info=El mensaje incluirá un enlace al documento actual. Solo los usuarios de RSpace con acceso al documento podrán usar el enlace.
messaging.dialog.multiple.docs.info=El mensaje incluirá enlaces a los documentos seleccionados. Solo los usuarios de RSpace con acceso a estos documentos podrán usar los enlaces.
template.publish.share.info=Configure el acceso a su formulario por grupos a los que pertenece, o por todos los usuarios de RSpace. Si no elige ninguna opción, el formulario permanecerá privado para usted.
```

#### French (`editor_fr.properties`):
```properties
button.edit=Modifier
button.delete=Supprimer
messaging.dialog.title=Envoyer un message à {0}
messaging.dialog.channel.label=Canal
messaging.dialog.message.label=Message
messaging.dialog.message.placeholder=Regardez mon document !
messaging.dialog.single.doc.info=Le message inclura un lien vers le document actuel. Seuls les utilisateurs RSpace ayant accès au document pourront utiliser le lien.
messaging.dialog.multiple.docs.info=Le message inclura des liens vers les documents sélectionnés. Seuls les utilisateurs RSpace ayant accès à ces documents pourront utiliser les liens.
template.publish.share.info=Configurez l'accès à votre formulaire par les groupes auxquels vous appartenez, ou par tous les utilisateurs RSpace. Si vous ne choisissez aucune option, le formulaire restera privé pour vous.
```

## 5. Benefits of Localization

1. **Improved User Experience**: Users can interact with the application in their preferred language
2. **Global Accessibility**: Makes the application accessible to international users
3. **Compliance**: Helps meet international accessibility and usability standards
4. **Market Expansion**: Enables expansion to non-English speaking markets
5. **User Retention**: Increases user satisfaction and retention in international markets

## 6. Best Practices

1. **Use Parameterized Messages**: Use `{0}`, `{1}`, etc. for dynamic values
2. **Maintain Consistency**: Keep message keys consistent across all language files
3. **Provide Context**: Include comments in properties files to provide context for translators
4. **Test Thoroughly**: Test with different languages to ensure proper display and functionality
5. **Follow Existing Patterns**: Use the same naming conventions and structure as existing localized messages