tinymce.PluginManager.add("videoembed", function (editor) {
  const FEEDBACK_ELEMENT_ID = "rspace-video-url-feedback";
  const HELP_MESSAGE = "Paste a supported video URL.";
  const PROVIDER_MESSAGE =
    "Supported providers: YouTube, YouTube Privacy-Enhanced Mode, JoVE, TIB AV-Portal.";

  function isHost(hostname, domain) {
    return hostname.toLowerCase() === domain || hostname.toLowerCase().endsWith("." + domain);
  }

  function isHttpUrl(parsedUrl) {
    return /^https?:$/i.test(parsedUrl.protocol);
  }

  function getYouTubeVideoId(parsedUrl) {
    if (isHost(parsedUrl.hostname, "youtu.be")) {
      const pathMatch = parsedUrl.pathname.match(/^\/([\w-]+)\/?$/);
      return pathMatch ? pathMatch[1] : null;
    }

    if (!isHost(parsedUrl.hostname, "youtube.com")) {
      return null;
    }

    const watchId = parsedUrl.searchParams.get("v");
    if (watchId && /^[\w-]+$/.test(watchId)) {
      return watchId;
    }

    const pathMatch = parsedUrl.pathname.match(/^\/(?:embed|shorts)\/([\w-]+)\/?$/);
    return pathMatch ? pathMatch[1] : null;
  }

  function parseYouTube(parsedUrl) {
    const videoId = getYouTubeVideoId(parsedUrl);
    if (!videoId) {
      return null;
    }

    return {
      valid: true,
      provider: "YouTube",
      iframe: {
        src: "https://www.youtube.com/embed/" + videoId,
        width: "560",
        height: "315",
        title: "YouTube video player",
        frameborder: "0",
        allow: "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share",
        allowfullscreen: "",
        referrerpolicy: "strict-origin-when-cross-origin",
      },
    };
  }

  function parseYouTubeNoCookie(parsedUrl) {
    if (!isHost(parsedUrl.hostname, "youtube-nocookie.com")) {
      return null;
    }

    const pathMatch = parsedUrl.pathname.match(/^\/embed\/([\w-]+)\/?$/);
    if (!pathMatch) {
      return null;
    }

    return {
      valid: true,
      provider: "YouTube Privacy-Enhanced Mode",
      iframe: {
        src: "https://www.youtube-nocookie.com/embed/" + pathMatch[1],
        width: "560",
        height: "315",
        title: "YouTube video player",
        frameborder: "0",
        allow: "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share",
        allowfullscreen: "",
        referrerpolicy: "strict-origin-when-cross-origin",
      },
    };
  }

  function parseJove(parsedUrl) {
    if (!isHost(parsedUrl.hostname, "jove.com")) {
      return null;
    }

    const embedId = parsedUrl.searchParams.get("id");
    if (/\/embed\/player\/?$/i.test(parsedUrl.pathname) && /^[0-9]+$/.test(embedId || "")) {
      return buildJoveResult(embedId);
    }

    const pathMatch = parsedUrl.pathname.match(/(?:^|\/)v\/([0-9]+)(?:\/|$)/i);
    return pathMatch ? buildJoveResult(pathMatch[1]) : null;
  }

  function buildJoveResult(articleId) {
    return {
      valid: true,
      provider: "JoVE",
      iframe: {
        src: "https://www.jove.com/embed/player?id=" + articleId + "&t=1&s=1&fpv=1",
        width: "460",
        height: "440",
        border: "0",
        frameborder: "0",
        marginwidth: "0",
        marginwheight: "0",
        allow: "encrypted-media *",
        allowfullscreen: "",
        allowtransparency: "true",
        scrolling: "no",
      },
    };
  }

  function parseTibAvPortal(parsedUrl) {
    if (parsedUrl.hostname.toLowerCase() !== "av.tib.eu") {
      return null;
    }

    const pathMatch = parsedUrl.pathname.match(/^\/(?:media|player)\/([0-9]+)\/?$/);
    if (!pathMatch) {
      return null;
    }

    return {
      valid: true,
      provider: "TIB AV-Portal",
      iframe: {
        src: "https://av.tib.eu/player/" + pathMatch[1],
        width: "560",
        allow: "fullscreen",
      },
    };
  }

  function getVideoEmbedFromUrl(value) {
    if (typeof value !== "string" || value.trim() === "") {
      return { valid: false, message: HELP_MESSAGE };
    }

    let parsedUrl;
    try {
      parsedUrl = new URL(value.trim());
    } catch (_error) {
      return { valid: false, message: "Enter a valid HTTP or HTTPS URL." };
    }

    if (!isHttpUrl(parsedUrl)) {
      return { valid: false, message: "Video URLs must start with http:// or https://." };
    }

    const parsedVideo =
      parseYouTubeNoCookie(parsedUrl) ||
      parseYouTube(parsedUrl) ||
      parseJove(parsedUrl) ||
      parseTibAvPortal(parsedUrl);

    return parsedVideo || { valid: false, message: "Enter a URL from a supported video provider." };
  }

  function buildEmbedHtml(iframeAttributes) {
    const iframeAttrs = Object.keys(iframeAttributes)
      .map(function (attrName) {
        return attrName + '="' + iframeAttributes[attrName] + '"';
      })
      .join(" ");

    return (
      '<div class="embedIframeDiv mceNonEditable">' +
      "<iframe " +
      iframeAttrs +
      "></iframe>" +
      "</div><p>&nbsp;</p>"
    );
  }

  function updateFeedback(message, isValid) {
    const feedback = document.getElementById(FEEDBACK_ELEMENT_ID);
    if (!feedback) {
      return;
    }
    feedback.textContent = message;
    feedback.style.color = isValid ? "#2e7d32" : "#c62828";
  }

  function syncDialogValidation(dialogApi) {
    const validation = getVideoEmbedFromUrl(dialogApi.getData().videoUrl);
    const message = validation.valid
      ? validation.provider + " video detected."
      : validation.message;
    if (validation.valid) {
      dialogApi.enable("submit");
    } else {
      dialogApi.disable("submit");
    }
    updateFeedback(message, validation.valid);
    return validation;
  }

  editor.addCommand("cmdVideoEmbed", function () {
    const dialogConfig = {
      title: "Embed video",
      size: "normal",
      body: {
        type: "panel",
        items: [
          {
            type: "input",
            name: "videoUrl",
            label: "Video URL",
            placeholder: "https://www.youtube.com/watch?v=...",
          },
          {
            type: "htmlpanel",
            html: "<p>" + PROVIDER_MESSAGE + "</p>",
          },
          {
            type: "htmlpanel",
            html:
              '<p id="' +
              FEEDBACK_ELEMENT_ID +
              '" aria-live="polite">' +
              HELP_MESSAGE +
              "</p>",
          },
        ],
      },
      initialData: {
        videoUrl: "",
      },
      buttons: [
        {
          type: "cancel",
          text: "Cancel",
        },
        {
          type: "submit",
          name: "submit",
          text: "Insert",
          primary: true,
          enabled: false,
        },
      ],
      onChange: function (dialogApi) {
        syncDialogValidation(dialogApi);
      },
      onSubmit: function (dialogApi) {
        const validation = syncDialogValidation(dialogApi);
        if (!validation.valid) {
          dialogApi.focus("videoUrl");
          return;
        }
        editor.focus();
        editor.execCommand("mceInsertContent", false, buildEmbedHtml(validation.iframe));
        dialogApi.close();
      },
    };

    const dialogApi = editor.windowManager.open(dialogConfig);
    syncDialogValidation(dialogApi);
  });

  function openVideoEmbedDialog() {
    editor.execCommand("cmdVideoEmbed");
    RS.trackEvent("VideoEmbedUsed");
  }

  editor.ui.registry.addButton("videoembed", {
    icon: "embed",
    tooltip: "Embed video",
    onAction: openVideoEmbedDialog,
  });

  editor.ui.registry.addMenuItem("optVideoEmbed", {
    text: "Video",
    icon: "embed",
    onAction: openVideoEmbedDialog,
  });

  if (!window.insertActions) {
    window.insertActions = new Map();
  }
  window.insertActions.set("optVideoEmbed", {
    text: "Video",
    icon: "embed",
    action: openVideoEmbedDialog,
  });
});
