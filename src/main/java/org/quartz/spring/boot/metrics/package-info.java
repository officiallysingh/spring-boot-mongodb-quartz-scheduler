/**
 * Micrometer meters for a Quartz {@link org.quartz.Scheduler}.
 *
 * <p>{@link org.quartz.spring.boot.metrics.QuartzMetrics} reports scheduler-wide gauges. {@link
 * org.quartz.spring.boot.metrics.QuartzJobMetrics} reports per-job execution, veto, and misfire
 * meters.
 */
package org.quartz.spring.boot.metrics;
