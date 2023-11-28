package com.chatbot.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerDocumentationConfig {

  private static final String BEARER_SCHEME = "bearerAuth";

  @Bean
  public OpenAPI apiInfo() {
    return new OpenAPI()
        .info(new Info()
            .title("Movie Recommendation Service")
            .version("1.0.0")
            .license(new License().url("http://unlicense.org")))
        .components(new Components().addSecuritySchemes(BEARER_SCHEME,
            new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Paste a JWT here. Use /auth/login for an admin token "
                    + "(required for the actuator and /internal endpoints) or /auth/google.")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
  }

  @Bean
  public GroupedOpenApi httpApi() {
    return GroupedOpenApi.builder().group("http").pathsToMatch("/**").build();
  }
}
