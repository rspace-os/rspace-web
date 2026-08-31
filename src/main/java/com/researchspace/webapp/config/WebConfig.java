package com.researchspace.webapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.researchspace.api.v1.controller.APIFileUploadThrottlingInterceptor;
import com.researchspace.api.v1.controller.APIRequestThrottlingInterceptor;
import com.researchspace.api.v1.controller.InventoryExportApiController;
import com.researchspace.api.v1.controller.InventoryFilesApiController;
import com.researchspace.api.v1.controller.InventoryImportApiController;
import com.researchspace.api.v2.controller.ApiV2AuthenticationInterceptor;
import com.researchspace.api.v2.controller.ApiV2ControllerAdvice;
import com.researchspace.api.v2.controller.ApiV2PreAuthenticationThrottlingInterceptor;
import com.researchspace.api.v2.controller.ApiV2PreHandlerProblemResolver;
import com.researchspace.api.v2.controller.ApiV2RequestThrottlingInterceptor;
import com.researchspace.webapp.integrations.wopi.WopiAuthorisationInterceptor;
import com.researchspace.webapp.integrations.wopi.WopiProofKeyValidationInterceptor;
import java.nio.charset.Charset;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.validation.Validator;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

/**
 * Beans configured here require a WebApplication context to be present and therefore this class
 * should be excluded from component-scans for non-web application contexts
 */
@Configuration
public class WebConfig extends WebMvcConfigurationSupport {

  @Autowired WebDefaultConfig defaultConfig;

  @Autowired APIRequestThrottlingInterceptor requestThrottle;
  @Autowired APIFileUploadThrottlingInterceptor fileUploadThrottle;
  @Autowired ApiV2RequestThrottlingInterceptor apiV2RequestThrottle;
  @Autowired ApiV2PreAuthenticationThrottlingInterceptor apiV2PreAuthenticationThrottle;
  @Autowired ApiV2AuthenticationInterceptor apiV2Authentication;
  @Autowired ApiV2ControllerAdvice apiV2ControllerAdvice;
  @Autowired ObjectMapper objectMapper;

  @Autowired WopiAuthorisationInterceptor wopiAuthorisation;
  @Autowired WopiProofKeyValidationInterceptor wopiProofKeyValidation;

  @Autowired
  @Qualifier("validator")
  private Validator jsonBackedValidator;

  @Value("${csrf.filters.enabled}")
  private String csrfFiltersEnabled;

  @Value("${api.fileuploadRateLimit.enabled:false}")
  private String fileuploadRateLimitEnabled;

  @Value("${api.permissiveCors.enabled:false}")
  private String permissiveCorsEnabled;

  @Value("${deployment.standalone}")
  private String standalone;

  /**
   * Without this, {@code mvcValidator()} builds its own validator, whose interpolator reads only
   * {@code ValidationMessages.properties}, and every {@code {some.key}} message reaches the user
   * with the braces intact.
   */
  @Override
  protected Validator getValidator() {
    return jsonBackedValidator;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(defaultConfig.performanceLoggingInterceptor()).addPathPatterns("/**");
    if ("true".equals(csrfFiltersEnabled)) {
      registry
          .addInterceptor(defaultConfig.originRefererCheckingInterceptor())
          .addPathPatterns("/**")
          .excludePathPatterns("/oauth/**", "/api/**", "/slack/callbacks/**", "/wopi/**");
      registry
          .addInterceptor(defaultConfig.originRefererCheckingInterceptor())
          .addPathPatterns("/api/v2/oauth/tokens");
    }
    // add timezone via cookie if possible, just needed for sso
    if ("false".equals(standalone)) {
      registry
          .addInterceptor(defaultConfig.timezoneInterceptor())
          .addPathPatterns("/**")
          .excludePathPatterns("/api/**", "/oauth/**", "/wopi/**");
    }

    registry
        .addInterceptor(defaultConfig.loggingInterceptor())
        .addPathPatterns("/**")
        .excludePathPatterns("/signup*", "/login*", "/adminLogin*"); // "/api/** to exclude API
    registry
        .addInterceptor(defaultConfig.browserCacheAdviceInterceptor())
        .addPathPatterns("/**")
        .excludePathPatterns("/api/**", "/wopi/**", "/oauth/**");
    registry
        .addInterceptor(defaultConfig.productAndPropertyAnnotationInterceptor())
        .addPathPatterns("/**");
    if ("true".equals(permissiveCorsEnabled)) {
      registry
          .addInterceptor(defaultConfig.apiPermissiveCorsInterceptor())
          .addPathPatterns("/api/**")
          .excludePathPatterns("/api/v2/**");
      // REST API v2 declares its policy through addCorsMappings instead; see the comment there.
    }
    registry
        .addInterceptor(requestThrottle)
        .addPathPatterns("/api/**")
        .excludePathPatterns("/api/v2/**");
    registry.addInterceptor(apiV2PreAuthenticationThrottle).addPathPatterns("/api/v2/**");
    if ("true".equals(fileuploadRateLimitEnabled)) {
      registry.addInterceptor(fileUploadThrottle).addPathPatterns("/api/**/files");
    }
    registry
        .addInterceptor(defaultConfig.apiAuthenticationInterceptor())
        .addPathPatterns("/api/**")
        .excludePathPatterns("/api/v2/**", "/api/inventory/v1/public/**");
    registry.addInterceptor(apiV2Authentication).addPathPatterns("/api/v2/**");
    registry.addInterceptor(apiV2RequestThrottle).addPathPatterns("/api/v2/**");
    registry.addInterceptor(wopiAuthorisation).addPathPatterns("/wopi/files/**");
    registry.addInterceptor(wopiProofKeyValidation).addPathPatterns("/wopi/files/**");
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    if (!"true".equals(permissiveCorsEnabled)) {
      return;
    }
    // A CorsConfiguration rather than a HandlerInterceptor. Spring decides a preflight from the
    // registered CorsConfiguration, so headers an interceptor sets later do not affect that
    // decision: an OPTIONS carrying Origin and Access-Control-Request-Method was refused with 403,
    // and every cross-origin POST/PATCH/DELETE and every request sending the apiKey header failed
    // in a browser while working from curl. Simple GETs did work, which is why the setting looked
    // functional.
    // Registered first: the source returns the first matching pattern, and the token route mints a
    // credential from the live browser session, so permissive API CORS must never reach it. No
    // allowed origin means every cross-origin attempt on it is refused.
    registry.addMapping("/api/v2/oauth/tokens").allowedOrigins();
    registry
        .addMapping("/api/v2/**")
        .allowedOrigins("*")
        .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("apiKey", "Authorization", "Content-Type")
        .maxAge(3600);
  }

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    registry.addViewController("/admin").setViewName("admin/admin");
    registry
        .addViewController("/public/signupConfirmation")
        .setViewName("public/signupConfirmation");
    registry
        .addViewController("/public/awaitingAuthorisation")
        .setViewName("public/awaitingAuthorisation");
    registry.addViewController("/public/accountDisabled").setViewName("public/accountDisabled");
    registry
        .addViewController("/public/requestPasswordReset")
        .setViewName("public/requestPasswordReset");
    registry
        .addViewController("/public/requestUsernameReminder")
        .setViewName("public/requestUsernameReminder");
    registry.addViewController("/public/terms").setViewName("public/terms");
    registry.addViewController("/public/ssoinfo").setViewName("public/ssoinfo");
    registry.addViewController("/public/noldapsignup").setViewName("public/noLdapSignUp");
    registry
        .addViewController("/public/ssoinfoUsernameConflict")
        .setViewName("public/ssoinfoUsernameConflict");
    registry
        .addViewController("/public/ssoinfoUsernameNotAlias")
        .setViewName("public/ssoinfoUsernameNotAlias");
    registry.addViewController("/public/ipAddressInvalid").setViewName("public/ipAddressInvalid");
    registry.addViewController("/audit/auditing").setViewName("audit/auditing");

    registry
        .addViewController("/externalTinymcePlugins/internalLink")
        .setViewName("externalTinymcePlugins/internalLink");

    registry.addViewController("/searchableRecordPicker").setViewName("searchableRecordPicker");

    registry.addViewController("/test/template").setViewName("test/template");
    registry
        .addViewController("/admin/cloud/createCloudGroupSuccess")
        .setViewName("admin/cloud/createCloudGroupSuccess");
    registry
        .addViewController("/cloud/signup/accountActivationComplete")
        .setViewName("cloud/signup/accountActivationComplete");
    registry
        .addViewController("/cloud/verifyEmailChange/emailChangeConfirmed")
        .setViewName("cloud/verifyEmailChange/emailChangeConfirmed");
    registry
        .addViewController("/cloud/resendConfirmationEmail/resendFailure")
        .setViewName("cloud/resendConfirmationEmail/resendFailure");
    registry
        .addViewController("/cloud/resendConfirmationEmail/resendSuccess")
        .setViewName("cloud/resendConfirmationEmail/resendSuccess");

    registry
        .addViewController("/msteams/domainConfig")
        .setViewName("connect/msteams/msTeamsDomainConfig");
    registry
        .addViewController("/msteams/rspaceAuthentication")
        .setViewName("connect/msteams/msTeamsRSpaceAuthentication");
    registry
        .addViewController("/msteams/tabConfig")
        .setViewName("connect/msteams/msTeamsTabConfig");
    registry
        .addViewController("/public/publishIsDisabled")
        .setViewName("/public/publishIsDisabled");
  }

  public static final class YamlJackson2HttpMessageConverter
      extends AbstractJackson2HttpMessageConverter {
    public YamlJackson2HttpMessageConverter() {
      super(new YAMLMapper(), MediaType.parseMediaType("application/x-yaml"));
    }
  }

  /**
   * Puts the v2 problem resolver ahead of Spring's own resolvers. Needed because {@link
   * ApiV2ControllerAdvice} is package-selected and so is skipped for exceptions raised before a
   * handler is chosen; see {@link ApiV2PreHandlerProblemResolver}.
   */
  @Override
  protected void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
    resolvers.add(0, new ApiV2PreHandlerProblemResolver(apiV2ControllerAdvice, objectMapper));
  }

  @Override
  protected void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
    for (HttpMessageConverter<?> converter : converters) {
      // Pretty-printing was on unconditionally, so every production response paid for indentation
      // and newlines on every field of every document. Nothing consumes the whitespace: browsers
      // and API clients format JSON themselves.
      if (converter instanceof MappingJackson2HttpMessageConverter) {
        ((MappingJackson2HttpMessageConverter) converter).setPrettyPrint(false);
      }
      // and string response bodies to be UTF8
      if (converter instanceof StringHttpMessageConverter) {
        ((StringHttpMessageConverter) converter).setDefaultCharset(Charset.forName("UTF-8"));
      }
    }
    // csv will be last
    converters.add(defaultConfig.csvApiErrorConverter());
    converters.add(defaultConfig.csvConverter());
    converters.add(new YamlJackson2HttpMessageConverter());
  }

  @Override
  public FormattingConversionService mvcConversionService() {
    FormattingConversionService f = super.mvcConversionService();
    f.addConverter(new InventoryFilesApiController.ApiInventoryFilePostConverter());
    f.addConverter(new InventoryFilesApiController.ApiInventoryFileImageRequestConverter());
    f.addConverter(new InventoryImportApiController.ApiInventoryImportPostConverter());
    f.addConverter(new InventoryExportApiController.ApiInventoryExportPostConverter());
    return f;
  }
}
