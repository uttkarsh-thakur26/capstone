package in.edu.bennett.capstone.knowledge.user;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({RegistrationProperties.class, BootstrapAdminProperties.class})
class UserConfig {}
