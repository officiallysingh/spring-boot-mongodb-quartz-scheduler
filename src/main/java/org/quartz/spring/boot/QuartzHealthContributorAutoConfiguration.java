package org.quartz.spring.boot;

import org.quartz.Scheduler;
import org.quartz.spring.boot.health.QuartzHealthIndicator;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.health.autoconfigure.contributor.CompositeHealthContributorConfiguration;
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for {@link QuartzHealthIndicator}.
 *
 * <p>Contributes one indicator per {@link Scheduler} bean to the {@code health} endpoint under the
 * name {@code quartz}. Disable it with {@code management.health.quartz.enabled=false}. An
 * application bean named {@code quartzHealthIndicator} or {@code quartzHealthContributor} replaces
 * this contribution.
 */
@AutoConfiguration(after = QuartzAutoConfiguration.class)
@ConditionalOnClass({
  Scheduler.class,
  QuartzHealthIndicator.class,
  ConditionalOnEnabledHealthIndicator.class
})
@ConditionalOnBean(Scheduler.class)
@ConditionalOnEnabledHealthIndicator("quartz")
public class QuartzHealthContributorAutoConfiguration
    extends CompositeHealthContributorConfiguration<QuartzHealthIndicator, Scheduler> {

  QuartzHealthContributorAutoConfiguration() {
    super(QuartzHealthIndicator::new);
  }

  /**
   * Builds the {@code quartz} health contributor from every {@link Scheduler} in the context.
   *
   * @param beanFactory factory used to find scheduler beans
   * @return a single indicator, or a composite when more than one scheduler exists
   */
  @Bean
  @ConditionalOnMissingBean(name = {"quartzHealthIndicator", "quartzHealthContributor"})
  HealthContributor quartzHealthContributor(ConfigurableListableBeanFactory beanFactory) {
    return createContributor(beanFactory, Scheduler.class);
  }
}
