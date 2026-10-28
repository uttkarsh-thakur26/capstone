package in.edu.bennett.capstone.knowledge.retrieval;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RetrievalProperties.class)
class RetrievalConfig {}
