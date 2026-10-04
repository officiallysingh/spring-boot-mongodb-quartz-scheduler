package org.quartz.spring.boot;

import org.quartz.Scheduler;
import org.quartz.spring.boot.actuate.endpoint.QuartzEndpoint;
import org.quartz.spring.boot.actuate.endpoint.QuartzEndpointWebExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.autoconfigure.endpoint.expose.EndpointExposure;
import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the {@link QuartzEndpoint}.
 *
 * <p>The endpoint bean is created when a {@link Scheduler} exists and the endpoint is available.
 * The web extension is created only when the endpoint is exposed over HTTP. Expose it with {@code
 * management.endpoints.web.exposure.include=quartz}. A {@link QuartzEndpoint} or {@link
 * QuartzEndpointWebExtension} bean declared by the application is left in place.
 */
@AutoConfiguration(after = QuartzAutoConfiguration.class)
@ConditionalOnClass({Scheduler.class, QuartzEndpoint.class, ConditionalOnAvailableEndpoint.class})
@ConditionalOnAvailableEndpoint(QuartzEndpoint.class)
@EnableConfigurationProperties(QuartzEndpointProperties.class)
public class QuartzEndpointAutoConfiguration {

  /**
   * Exposes jobs and triggers from the auto-configured or user-declared {@link Scheduler}.
   *
   * @param scheduler the scheduler to read
   * @param sanitizingFunctions additional rules applied to job and trigger data values
   * @return the {@code quartz} endpoint
   */
  @Bean
  @ConditionalOnBean(Scheduler.class)
  @ConditionalOnMissingBean
  QuartzEndpoint quartzEndpoint(
      Scheduler scheduler, ObjectProvider<SanitizingFunction> sanitizingFunctions) {
    return new QuartzEndpoint(scheduler, sanitizingFunctions.orderedStream().toList());
  }

  /**
   * Serves {@code /actuator/quartz/jobs} and {@code /actuator/quartz/triggers}, including group and
   * item selectors and the write operation that fires a job.
   *
   * @param endpoint the endpoint that reads the scheduler
   * @param properties {@code show-values} and {@code roles} for unsanitized data maps
   * @return the web extension
   */
  @Bean
  @ConditionalOnBean(QuartzEndpoint.class)
  @ConditionalOnMissingBean
  @ConditionalOnAvailableEndpoint(exposure = EndpointExposure.WEB)
  QuartzEndpointWebExtension quartzEndpointWebExtension(
      QuartzEndpoint endpoint, QuartzEndpointProperties properties) {
    return new QuartzEndpointWebExtension(
        endpoint, properties.getShowValues(), properties.getRoles());
  }
}
