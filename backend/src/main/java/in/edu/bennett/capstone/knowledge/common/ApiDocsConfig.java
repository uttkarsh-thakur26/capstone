package in.edu.bennett.capstone.knowledge.common;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documents the error format once and attaches it to every operation (ADR-023). */
@Configuration(proxyBeanMethods = false)
class ApiDocsConfig {

  private static final String PROBLEM_JSON = "application/problem+json";

  @Bean
  OpenApiCustomizer problemResponses() {
    return openApi -> {
      Components components = openApi.getComponents();
      if (components == null) {
        components = new Components();
        openApi.setComponents(components);
      }
      ModelConverters.getInstance().readAll(ProblemResponse.class).forEach(components::addSchemas);
      ApiResponse problem =
          new ApiResponse()
              .description("Problem Details")
              .content(
                  new Content()
                      .addMediaType(
                          PROBLEM_JSON,
                          new MediaType()
                              .schema(
                                  new Schema<>().$ref("#/components/schemas/ProblemResponse"))));
      if (openApi.getPaths() != null) {
        openApi
            .getPaths()
            .values()
            .forEach(
                path ->
                    path.readOperations()
                        .forEach(
                            operation ->
                                operation.getResponses().addApiResponse("default", problem)));
      }
    };
  }
}
