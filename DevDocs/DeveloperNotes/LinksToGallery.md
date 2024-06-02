Links to gallery are now parsed in gallery.jsp and a var galleryMediaTypeFromUrlParameter is set:
```
<script>
    const urlParams = new URLSearchParams(window.location.search);
    var galleryMediaTypeFromUrlParameter = urlParams.get('mediaType');
    var galleryCurrentFolderIdFromUrlParameter = '${currentFolderId}';
    var galleryOptionalSearchTermFromUrlParameter = "";
    <c:if test="${not empty term}">
        galleryOptionalSearchTermFromUrlParameter = '${term}';
    </c:if>
</script>
```

This value is then used by gallery.js
```
var selectedGallery = galleryMediaTypeFromUrlParameter || "Images";
$("#mediaTypeSelected").val(selectedGallery);
```
Based on the value in the div with id mediaTypeSelected, the js code shows the appropriate section of the gallery

Therefore a url http://localhost:8080/gallery?mediaType=Snippets will now open the snippets section of the gallery.

http://localhost:8080/gallery?mediaType=Chemistry will open chemistry etc