package org.quartz.spring.boot;

import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.quartz.spring.boot.actuate.endpoint.QuartzEndpoint;
import org.springframework.boot.actuate.endpoint.Show;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code management.endpoint.quartz.*} settings for the {@link QuartzEndpoint}.
 *
 * <p>{@code show-values} controls when unsanitized job and trigger data maps are included in the
 * response. {@code roles} limits that exposure to authenticated users who have one of the roles. An
 * empty role set authorizes every authenticated user.
 */
@Getter
@ConfigurationProperties("management.endpoint.quartz")
public class QuartzEndpointProperties {

  /** When to show unsanitized job or trigger values. */
  @Setter private Show showValues = Show.NEVER;

  /**
   * Roles used to determine whether a user is authorized to be shown unsanitized job or trigger
   * values. When empty, all authenticated users are authorized.
   */
  private final Set<String> roles = new HashSet<>();
}
