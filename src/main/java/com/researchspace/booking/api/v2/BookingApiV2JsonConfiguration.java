package com.researchspace.booking.api.v2;

import com.researchspace.api.v2.json.ApiV2JsonBodyTypes;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** JSON binding contracts owned by the feature. */
@Configuration(proxyBeanMethods = false)
public class BookingApiV2JsonConfiguration {
  @Bean
  public ApiV2JsonBodyTypes strictRequestBodyTypes() {
    return new ApiV2JsonBodyTypes(
        List.of(
            BookingSettingsController.SettingsPatch.class,
            BookingDisplayPreferencesController.Replacement.class));
  }
}
