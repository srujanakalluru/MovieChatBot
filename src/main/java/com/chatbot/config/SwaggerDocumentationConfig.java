package com.chatbot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerDocumentationConfig {
  OpenAPI apiInfo() {
    OpenAPI openAPI = new OpenAPI();

    License license = new License();
    license.setUrl("http://unlicense.org");

    Info info = new Info();
    info.title("Movie Recommendation Service");
    info.version("1.0.0");
    info.license(license);

    openAPI.info(info);

    return openAPI;
  }

  @Bean
  public GroupedOpenApi httpApi() {
    return GroupedOpenApi.builder().group("http").pathsToMatch("/**").build();
  }
}
