package com.researchspace.linkedelements;

import static com.researchspace.core.util.FieldParserConstants.ATTACHMENT_CLASSNAME;
import static com.researchspace.core.util.FieldParserConstants.AUDIO_CLASSNAME;
import static com.researchspace.core.util.FieldParserConstants.CHEM_IMG_CLASSNAME;
import static com.researchspace.core.util.FieldParserConstants.COMMENT_CLASS_NAME;
import static com.researchspace.core.util.FieldParserConstants.IMAGE_DROPPED_CLASS_NAME;
import static com.researchspace.core.util.FieldParserConstants.LINKEDRECORD_CLASS_NAME;
import static com.researchspace.core.util.FieldParserConstants.MATH_CLASSNAME;
import static com.researchspace.core.util.FieldParserConstants.SKETCH_IMG_CLASSNAME;
import static com.researchspace.core.util.FieldParserConstants.VIDEO_CLASSNAME;

import com.researchspace.core.util.FieldParserConstants;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LinkedElementConfig {

  @Bean
  FieldElementConverter attachmentConverter() {
    return new AttachmentConverter();
  }

  @Bean
  FieldElementConverter audioConverter() {
    return new AudioConverter();
  }

  @Bean
  FieldElementConverter chemConverter() {
    return new ChemConverter();
  }

  @Bean
  FieldElementConverter commentConverter() {
    return new CommentConverter();
  }

  @Bean
  FieldElementConverter imageConverter() {
    return new ImageConverter();
  }

  @Bean
  FieldElementConverter linkedRecordConverter() {
    return new LinkedRecordConverter();
  }

  @Bean
  FieldElementConverter mathConverter() {
    return new MathConverter();
  }

  @Bean
  FieldElementConverter sketchConverter() {
    return new SketchConverter();
  }

  @Bean
  FieldElementConverter videoConverter() {
    return new VideoConverter();
  }

  @Bean
  FieldElementConverter nfsConverter() {
    return new NfsConverter();
  }

  @Bean
  FieldParserFactoryImpl fieldConverterFactory() {
    FieldParserFactoryImpl factory = new FieldParserFactoryImpl();
    Map<String, FieldElementConverter> cssToParser = new HashMap<>();
    cssToParser.put(AUDIO_CLASSNAME, audioConverter());
    cssToParser.put(VIDEO_CLASSNAME, videoConverter());
    cssToParser.put(LINKEDRECORD_CLASS_NAME, linkedRecordConverter());
    cssToParser.put(SKETCH_IMG_CLASSNAME, sketchConverter());
    cssToParser.put(COMMENT_CLASS_NAME, commentConverter());
    cssToParser.put(CHEM_IMG_CLASSNAME, chemConverter());
    cssToParser.put(MATH_CLASSNAME, mathConverter());
    cssToParser.put(ATTACHMENT_CLASSNAME, attachmentConverter());
    // t his handles images and annotations
    cssToParser.put(IMAGE_DROPPED_CLASS_NAME, imageConverter());
    cssToParser.put(FieldParserConstants.NET_FS_CLASSNAME, nfsConverter());
    factory.setConverters(cssToParser);
    return factory;
  }

  @Bean
  ElementSelectorFactory elementSelectorFactory() {
    return new ElementSelectorFactory();
  }
}
