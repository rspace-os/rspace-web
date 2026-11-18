package com.researchspace.model.dtos;

import com.researchspace.model.field.ChoiceFieldForm;
import com.researchspace.model.field.DateFieldForm;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.field.NumberFieldForm;
import com.researchspace.model.field.RadioFieldForm;
import com.researchspace.model.field.StringFieldForm;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.field.TimeFieldForm;
import java.util.EnumMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Validator;

/**
 * Maps between ELN form fields and the legacy field DTOs that the form field editor reads and
 * writes, one DTO class per field type.
 */
public final class LegacyFormFieldDTOs {

  private static final Map<FieldType, Class<? extends AbstractFormFieldDTO<?>>> DTO_CLASSES =
      new EnumMap<>(FieldType.class);

  static {
    DTO_CLASSES.put(FieldType.NUMBER, rawClass(NumberFieldDTO.class));
    DTO_CLASSES.put(FieldType.STRING, rawClass(StringFieldDTO.class));
    DTO_CLASSES.put(FieldType.TEXT, rawClass(TextFieldDTO.class));
    DTO_CLASSES.put(FieldType.RADIO, rawClass(RadioFieldDTO.class));
    DTO_CLASSES.put(FieldType.CHOICE, rawClass(ChoiceFieldDTO.class));
    DTO_CLASSES.put(FieldType.DATE, rawClass(DateFieldDTO.class));
    DTO_CLASSES.put(FieldType.TIME, rawClass(TimeFieldDTO.class));
    DTO_CLASSES.put(FieldType.URI, UriFieldDTO.class);
    DTO_CLASSES.put(FieldType.REFERENCE, ReferenceFieldDTO.class);
    DTO_CLASSES.put(FieldType.ATTACHMENT, AttachmentFieldDTO.class);
  }

  private LegacyFormFieldDTOs() {}

  @SuppressWarnings("unchecked") // the legacy DTOs' type parameters are unused
  private static Class<? extends AbstractFormFieldDTO<?>> rawClass(Class<?> dtoClass) {
    return (Class<? extends AbstractFormFieldDTO<?>>) dtoClass;
  }

  /**
   * @param type a field type as {@link FieldType#getType()} spells it, for example {@code Radio}
   * @return the DTO class for that type, or null if the type is unknown
   */
  public static Class<? extends AbstractFormFieldDTO<?>> dtoClassFor(String type) {
    FieldType fieldType = type == null ? null : FieldType.getFieldTypeForString(type);
    return fieldType == null ? null : DTO_CLASSES.get(fieldType);
  }

  /**
   * The validator for a legacy DTO, or null if {@code source} is not a legacy DTO (for example a
   * forms API field, which the API validates itself).
   */
  public static Validator validatorFor(Object source) {
    if (source instanceof NumberFieldDTO) {
      return new NumberFieldDTOValidator();
    } else if (source instanceof StringFieldDTO) {
      return new StringFieldDTOValidator();
    } else if (source instanceof TextFieldDTO) {
      return new TextFieldValidator();
    } else if (source instanceof RadioFieldDTO) {
      return new RadioFieldDTOValidator();
    } else if (source instanceof ChoiceFieldDTO) {
      return new ChoiceFieldDTOValidator();
    } else if (source instanceof DateFieldDTO) {
      return new DateFieldDTOValidator();
    } else if (source instanceof TimeFieldDTO) {
      return new TimeFieldDTOValidator();
    } else if (source instanceof UriFieldDTO
        || source instanceof ReferenceFieldDTO
        || source instanceof AttachmentFieldDTO) {
      return new NameOnlyFieldDTOValidator();
    }
    return null;
  }

  /**
   * Prepares a legacy DTO read from JSON for validation. Radio and choice DTOs carry the name twice
   * (their validators check {@code fieldName}, the copy into the field uses {@code name}), so
   * whichever is blank is filled from the other. Absent yes/no flags mean no.
   */
  public static void normalise(Object source) {
    if (source instanceof RadioFieldDTO<?> radio) {
      radio.setFieldName(firstNonBlank(radio.getFieldName(), radio.getName()));
      radio.setName(firstNonBlank(radio.getName(), radio.getFieldName()));
    } else if (source instanceof ChoiceFieldDTO<?> choice) {
      choice.setFieldName(firstNonBlank(choice.getFieldName(), choice.getName()));
      choice.setName(firstNonBlank(choice.getName(), choice.getFieldName()));
      choice.setMultipleChoice(firstNonBlank(choice.getMultipleChoice(), "no"));
    } else if (source instanceof StringFieldDTO<?> string) {
      string.setIfPassword(firstNonBlank(string.getIfPassword(), "no"));
    }
  }

  private static String firstNonBlank(String first, String second) {
    return StringUtils.isBlank(first) ? second : first;
  }

  /** The legacy DTO holding every setting of {@code field}. */
  public static AbstractFormFieldDTO<?> fromFieldForm(FieldForm field) {
    String name = field.getName();
    boolean mandatory = field.isMandatory();
    return switch (field.getType()) {
      case NUMBER -> {
        NumberFieldForm number = (NumberFieldForm) field;
        yield new NumberFieldDTO<>(
            numberToString(number.getMinNumberValue()),
            numberToString(number.getMaxNumberValue()),
            number.getDecimalPlaces() == null ? "" : number.getDecimalPlaces().toString(),
            numberToString(number.getDefaultNumberValue()),
            FieldType.NUMBER,
            name,
            mandatory);
      }
      case STRING -> {
        StringFieldForm string = (StringFieldForm) field;
        yield new StringFieldDTO<>(
            name, mandatory, yesNo(string.isIfPassword()), string.getDefaultStringValue());
      }
      case TEXT -> new TextFieldDTO<>(name, mandatory, ((TextFieldForm) field).getDefaultValue());
      case RADIO -> {
        RadioFieldForm radio = (RadioFieldForm) field;
        yield new RadioFieldDTO<>(
            radio.getRadioOption(),
            radio.getDefaultRadioOption(),
            name,
            radio.isShowAsPickList(),
            radio.isSortAlphabetic(),
            mandatory);
      }
      case CHOICE -> {
        ChoiceFieldForm choice = (ChoiceFieldForm) field;
        yield new ChoiceFieldDTO<>(
            choice.getChoiceOptions(),
            yesNo(choice.isMultipleChoice()),
            choice.getDefaultChoiceOption(),
            name,
            mandatory);
      }
      case DATE -> {
        DateFieldForm date = (DateFieldForm) field;
        yield new DateFieldDTO<>(
            date.getDefaultDateAsString(),
            date.getMinDateAsString(),
            date.getMaxDateAsString(),
            date.getFormat(),
            name,
            mandatory);
      }
      case TIME -> {
        TimeFieldForm time = (TimeFieldForm) field;
        yield new TimeFieldDTO<>(
            time.getDefaultTimeAsString(),
            time.getMinTimeAsString(),
            time.getmaxTimeAsString(),
            time.getTimeFormat(),
            name,
            mandatory);
      }
      case URI -> new UriFieldDTO(name, mandatory);
      case REFERENCE -> new ReferenceFieldDTO(name, mandatory);
      case ATTACHMENT -> new AttachmentFieldDTO(name, mandatory);
      default ->
          throw new IllegalArgumentException("Unsupported form field type " + field.getType());
    };
  }

  private static String numberToString(Double value) {
    return value == null ? "" : value.toString();
  }

  private static String yesNo(boolean value) {
    return value ? "yes" : "no";
  }
}
