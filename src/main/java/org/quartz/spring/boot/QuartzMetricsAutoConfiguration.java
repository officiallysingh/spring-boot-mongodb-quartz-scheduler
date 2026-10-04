package org.quartz.spring.boot;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.quartz.Scheduler;
import org.quartz.spring.boot.metrics.QuartzJobMetrics;
import org.quartz.spring.boot.metrics.QuartzMetrics;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Quartz Micrometer metrics.
 *
 * <p>Registers {@link QuartzMetrics} and {@link QuartzJobMetrics} when a {@link Scheduler} and a
 * {@link MeterRegistry} are present. Spring Boot binds those {@link MeterBinder} beans to every
 * registry. Turn individual meters off with {@code management.metrics.enable.quartz*}. An
 * application-declared binder of the same type replaces the auto-configured one.
 */
@AutoConfiguration(
    after = QuartzAutoConfiguration.class,
    afterName = {
      "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
      "org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration"
    })
@ConditionalOnClass({Scheduler.class, MeterBinder.class})
@ConditionalOnBean({Scheduler.class, MeterRegistry.class})
public class QuartzMetricsAutoConfiguration {

  /**
   * Binds scheduler-wide gauges and the jobs-executed counter.
   *
   * @param scheduler the scheduler whose in-memory state is scraped
   * @return the scheduler meter binder
   */
  @Bean
  @ConditionalOnMissingBean
  QuartzMetrics quartzMetrics(Scheduler scheduler) {
    return new QuartzMetrics(scheduler);
  }

  /**
   * Binds per-job execution, active, veto, and misfire meters through scheduler listeners.
   *
   * @param scheduler the scheduler the listeners are registered on
   * @return the per-job meter binder
   */
  @Bean
  @ConditionalOnMissingBean
  QuartzJobMetrics quartzJobMetrics(Scheduler scheduler) {
    return new QuartzJobMetrics(scheduler);
  }
}
