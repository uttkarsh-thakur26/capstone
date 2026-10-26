package in.edu.bennett.capstone.knowledge.document;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(UploadProperties.class)
class DocumentConfig {

  /** Room for the metadata part and the multipart framing next to the file itself. */
  private static final long FORM_OVERHEAD_BYTES = 1024 * 1024;

  /**
   * The servlet container's multipart limits, derived from {@code app.upload.max-file-size} so
   * there is one setting. A larger request is stopped before it reaches the controller and answers
   * {@code 413} with {@code FILE_TOO_LARGE}. Replaces Spring Boot's own, which reads {@code
   * spring.servlet.multipart.*}.
   */
  @Bean
  MultipartConfigElement multipartConfigElement(UploadProperties upload) {
    long max = upload.maxFileSize().toBytes();
    return new MultipartConfigElement("", max, max + FORM_OVERHEAD_BYTES, 0);
  }
}
