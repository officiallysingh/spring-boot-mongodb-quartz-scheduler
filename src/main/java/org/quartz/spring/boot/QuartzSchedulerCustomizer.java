package org.quartz.spring.boot;

/**
 * Callback to fine-tune the auto-configured {@link QuartzSchedulerFactoryBean}, the same role as
 * Spring Boot's {@code SchedulerFactoryBeanCustomizer}.
 *
 * <p>Beans of this type are applied, in {@link org.springframework.core.Ordered} order, after
 * {@link QuartzAutoConfiguration} has copied {@link QuartzProperties} and the discovered jobs,
 * triggers, calendars, and plugins onto the factory. A customizer may replace any of those
 * settings. It runs before the factory creates the {@link org.quartz.Scheduler}.
 */
@FunctionalInterface
public interface QuartzSchedulerCustomizer {

  /**
   * Adjusts the factory that will create the auto-configured scheduler.
   *
   * @param factoryBean the factory populated from {@link QuartzProperties} and scheduler beans
   */
  void customize(QuartzSchedulerFactoryBean factoryBean);
}
