package in.edu.bennett.capstone.knowledge.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint whose requests count against a rate limit (SEC-8). The security module's
 * interceptor checks the limit before the controller method runs, so a refused request does no work
 * at all. It lives here, in {@code common}, so that every module can mark its endpoints.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

  Kind value();

  enum Kind {
    /** Asking, summarizing and comparing: one budget per user, per minute and per day. */
    AI,
    /** Registration: per client address, because nobody is signed in yet. */
    REGISTRATION
  }
}
