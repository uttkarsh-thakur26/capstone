package in.edu.bennett.capstone.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class KnowledgePlatformApplicationTests {

  @Test
  void contextLoads() {}
}
