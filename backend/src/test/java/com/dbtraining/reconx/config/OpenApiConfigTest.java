package com.dbtraining.reconx.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void exposesReconxMetadataAndBearerAuthentication() {
        OpenAPI openApi = config.reconxOpenAPI();

        assertThat(openApi.getInfo().getTitle()).isEqualTo("ReconX API");
        assertThat(openApi.getInfo().getDescription()).isNotBlank();
        assertThat(openApi.getInfo().getVersion()).isNotBlank();
        assertThat(openApi.getInfo().getContact().getName()).isNotBlank();
        assertThat(openApi.getComponents().getSecuritySchemes())
                .containsKey("bearerAuth");

        SecurityScheme bearerAuth = openApi.getComponents().getSecuritySchemes().get("bearerAuth");
        assertThat(bearerAuth.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(bearerAuth.getScheme()).isEqualTo("bearer");
        assertThat(bearerAuth.getBearerFormat()).isEqualTo("JWT");
    }

    @Test
    void groupsPublicAndAdminPathsSeparately() {
        GroupedOpenApi publicApi = config.publicApi();
        GroupedOpenApi adminApi = config.adminApi();

        assertThat(publicApi.getGroup()).isEqualTo("public");
        assertThat(publicApi.getPathsToMatch())
                .containsExactlyInAnyOrder("/v1/trades/**", "/v1/recon/**");
        assertThat(adminApi.getGroup()).isEqualTo("admin");
        assertThat(adminApi.getPathsToMatch())
                .containsExactlyInAnyOrder("/v1/admin/**", "/actuator/**");
    }

    @Test
    void exposesGroupedDocumentsAtTheWorkshopApiDocsPath() throws Exception {
        String applicationYaml = new ClassPathResource("application.yml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(applicationYaml).contains("path: /v1/api-docs");
        assertThat(applicationYaml).contains("context-path: /api");
    }
}
