package in.edu.bennett.capstone.knowledge.common;

import org.springframework.boot.info.BuildProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The one public endpoint besides health: the product name and the build version. */
@RestController
class MetaController {

  private final MetaResponse meta;

  MetaController(AppProperties properties, BuildProperties build) {
    this.meta = new MetaResponse(properties.displayName(), build.getVersion());
  }

  @GetMapping(path = "/api/v1/meta", produces = MediaType.APPLICATION_JSON_VALUE)
  MetaResponse meta() {
    return meta;
  }
}
