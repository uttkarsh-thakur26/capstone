package in.edu.bennett.capstone.knowledge.common;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppProperties.class)
class CommonConfig {}
