package in.edu.bennett.capstone.knowledge.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** AUTH-1: the optional allowed-domain list. */
class RegistrationPropertiesTest {

  @Test
  void withoutAListEveryDomainMayRegister() {
    assertThat(new RegistrationProperties(null).allows("a@anywhere.example")).isTrue();
    assertThat(new RegistrationProperties(List.of()).allows("a@anywhere.example")).isTrue();
  }

  @Test
  void withAListOnlyThoseDomainsMayRegister() {
    RegistrationProperties properties = new RegistrationProperties(List.of(" Uni.Example ", ""));

    assertThat(properties.allows("student@uni.example")).isTrue();
    assertThat(properties.allows("student@other.example")).isFalse();
    assertThat(properties.allows("student@mail.uni.example")).isFalse();
    assertThat(properties.allows("uni.example@other.example")).isFalse();
  }
}
