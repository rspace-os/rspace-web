package com.researchspace.webapp.config;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.researchspace.api.v1.controller.APIFileUploadThrottlingInterceptor;
import com.researchspace.api.v1.controller.APIRequestThrottlingInterceptor;
import com.researchspace.api.v1.controller.InventoryExportApiController;
import com.researchspace.api.v1.controller.InventoryFilesApiController;
import com.researchspace.api.v1.controller.InventoryImportApiController;
import com.researchspace.webapp.integrations.wopi.WopiAuthorisationInterceptor;
import com.researchspace.webapp.integrations.wopi.WopiProofKeyValidationInterceptor;
import java.nio.charset.Charset;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
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

  @Autowired WopiAuthorisationInterceptor wopiAuthorisation;
  @Autowired WopiProofKeyValidationInterceptor wopiProofKeyValidation;

  @Value("${csrf.filters.enabled}")
  private String csrfFiltersEnabled;

  @Value("${api.fileuploadRateLimit.enabled:false}")
  private String fileuploadRateLimitEnabled;

  @Value("${api.permissiveCors.enabled:false}")
  private String permissiveCorsEnabled;

  @Value("${deployment.standalone}")
  private String standalone;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(defaultConfig.performanceLoggingInterceptor()).addPathPatterns("/**");
    if ("true".equals(csrfFiltersEnabled)) {
      registry
          .addInterceptor(defaultConfig.originRefererCheckingInterceptor())
          .addPathPatterns("/**")
          .excludePathPatterns("/oauth/**", "/api/**", "/slack/callbacks/**", "/wopi/**");
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
          .addPathPatterns("/api/**");
    }
    registry.addInterceptor(requestThrottle).addPathPatterns("/api/**");
    if ("true".equals(fileuploadRateLimitEnabled)) {
      registry.addInterceptor(fileUploadThrottle).addPathPatterns("/api/**/files");
    }
    registry
        .addInterceptor(defaultConfig.apiAuthenticationInterceptor())
        .addPathPatterns("/api/**")
        .excludePathPatterns("/api/inventory/v1/public/**");
    registry.addInterceptor(wopiAuthorisation).addPathPatterns("/wopi/files/**");
    registry.addInterceptor(wopiProofKeyValidation).addPathPatterns("/wopi/files/**");
  }

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    registry.addViewController("/styleGuide").setViewName("styleGuide/styleGuide");
    registry.addViewController("/tools").setViewName("tools/tools");
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
    registry
        .addViewController("/public/maintenanceInProgress")
        .setViewName("public/maintenanceInProgress");
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
    registry.addViewController("/public/apiDocs").setViewName("public/apiDocs");
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

  @Override
  protected void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
    // now let's reconfigure Jackson to use PrettyPrinted object mapper.
    for (HttpMessageConverter<?> converter : converters) {
      if (converter instanceof MappingJackson2HttpMessageConverter) {
        ((MappingJackson2HttpMessageConverter) converter).setPrettyPrint(true);
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
