package com.researchspace.service.archive.export;

import lombok.Value;

@Value
class RelativeLinks {

  private String relativeLinkToReplaceLinkInText;
  private String linkToOriginalFile;

  RelativeLinks(String relativeLinkToReplaceLinkInText, String linkToOriginalFile) {
    super();
    this.relativeLinkToReplaceLinkInText = relativeLinkToReplaceLinkInText;
    this.linkToOriginalFile = linkToOriginalFile;
  }
}
