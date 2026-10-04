/**
 * Actuator endpoint that reports Quartz jobs and triggers.
 *
 * <p>Fire times in the payload are {@link java.time.Instant} values. The HTTP paths are served by
 * {@link org.quartz.spring.boot.actuate.endpoint.QuartzEndpointWebExtension}.
 */
package org.quartz.spring.boot.actuate.endpoint;
