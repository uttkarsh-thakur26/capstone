package in.edu.bennett.capstone.knowledge;

import org.springframework.boot.SpringApplication;

public class TestKnowledgePlatformApplication {

	public static void main(String[] args) {
		SpringApplication.from(KnowledgePlatformApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
