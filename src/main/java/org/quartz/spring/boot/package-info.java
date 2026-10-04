/**
 * Spring Boot integration for the MongoDB Quartz scheduler.
 *
 * <p>{@link org.quartz.spring.boot.QuartzAutoConfiguration} builds a {@link
 * org.quartz.spring.boot.QuartzSchedulerFactoryBean} from {@code quartz.scheduler.*} and the
 * application's MongoDB client. Actuator health, the {@code quartz} endpoint, and Micrometer meters
 * are configured by the other auto-configuration classes in this package when those dependencies
 * are present.
 */
package org.quartz.spring.boot;
