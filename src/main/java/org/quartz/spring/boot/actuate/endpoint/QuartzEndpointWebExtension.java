package org.quartz.spring.boot.actuate.endpoint;

import java.util.Set;
import org.quartz.SchedulerException;
import org.quartz.spring.boot.actuate.endpoint.QuartzEndpoint.QuartzGroupsDescriptor;
import org.quartz.spring.boot.actuate.endpoint.QuartzEndpoint.QuartzJobDetailsDescriptor;
import org.quartz.spring.boot.actuate.endpoint.QuartzEndpoint.QuartzJobGroupSummaryDescriptor;
import org.quartz.spring.boot.actuate.endpoint.QuartzEndpoint.QuartzTriggerGroupSummaryDescriptor;
import org.quartz.spring.boot.actuate.endpoint.QuartzEndpointWebExtension.QuartzEndpointWebExtensionRuntimeHints;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.endpoint.Show;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.EndpointWebExtension;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Web extension for the {@link QuartzEndpoint}.
 *
 * <p>Exposes {@code /actuator/quartz/jobs} and {@code /actuator/quartz/triggers}, plus {@code
 * /{group}} and {@code /{group}/{name}} under each. The first path selector must be {@code jobs} or
 * {@code triggers}; any other value is a bad request. An unknown group or name is reported as not
 * found.
 *
 * <p>A POST to {@code /actuator/quartz/jobs/{group}/{name}} with {@code state=running} fires that
 * job now. Unsanitized job and trigger data is included only when {@link Show} allows it for the
 * caller.
 */
@EndpointWebExtension(endpoint = QuartzEndpoint.class)
@ImportRuntimeHints(QuartzEndpointWebExtensionRuntimeHints.class)
public class QuartzEndpointWebExtension {

  private final QuartzEndpoint delegate;

  private final Show showValues;

  private final Set<String> roles;

  /**
   * Creates the web extension.
   *
   * @param delegate the endpoint that reads the scheduler
   * @param showValues when unsanitized data maps may be returned
   * @param roles roles allowed to see unsanitized values; empty means every authenticated user
   */
  public QuartzEndpointWebExtension(QuartzEndpoint delegate, Show showValues, Set<String> roles) {
    this.delegate = delegate;
    this.showValues = showValues;
    this.roles = roles;
  }

  /**
   * Lists job groups or trigger groups.
   *
   * @param jobsOrTriggers {@code jobs} or {@code triggers}
   * @return the groups, or status 400 when the selector is neither
   * @throws SchedulerException if the scheduler cannot list groups
   */
  @ReadOperation
  public WebEndpointResponse<QuartzGroupsDescriptor> quartzJobOrTriggerGroups(
      @Selector String jobsOrTriggers) throws SchedulerException {
    return handle(
        jobsOrTriggers, this.delegate::quartzJobGroups, this.delegate::quartzTriggerGroups);
  }

  /**
   * Returns one job group or one trigger group.
   *
   * @param jobsOrTriggers {@code jobs} or {@code triggers}
   * @param group the group name
   * @return the group summary, status 404 when the group is unknown, or status 400 when the
   *     selector is neither {@code jobs} nor {@code triggers}
   * @throws SchedulerException if the scheduler cannot read the group
   */
  @ReadOperation
  public WebEndpointResponse<Object> quartzJobOrTriggerGroup(
      @Selector String jobsOrTriggers, @Selector String group) throws SchedulerException {
    return handle(
        jobsOrTriggers,
        () -> this.delegate.quartzJobGroupSummary(group),
        () -> this.delegate.quartzTriggerGroupSummary(group));
  }

  /**
   * Returns one job or one trigger. Data maps are unsanitized only when {@code showValues} allows
   * it for {@code securityContext}.
   *
   * @param securityContext the caller, used to decide whether values are shown raw
   * @param jobsOrTriggers {@code jobs} or {@code triggers}
   * @param group the group name
   * @param name the job or trigger name
   * @return the details, status 404 when the item is unknown, or status 400 for a bad selector
   * @throws SchedulerException if the scheduler cannot read the item
   */
  @ReadOperation
  public WebEndpointResponse<Object> quartzJobOrTrigger(
      SecurityContext securityContext,
      @Selector String jobsOrTriggers,
      @Selector String group,
      @Selector String name)
      throws SchedulerException {
    boolean showUnsanitized = this.showValues.isShown(securityContext, this.roles);
    return handle(
        jobsOrTriggers,
        () -> this.delegate.quartzJob(group, name, showUnsanitized),
        () -> this.delegate.quartzTrigger(group, name, showUnsanitized));
  }

  /**
   * Fires a job now when the first selector is {@code jobs} and {@code state} is {@code running}.
   *
   * @param jobs must be the literal {@code jobs}
   * @param group the job group
   * @param name the job name
   * @param state must be {@code running}
   * @return the fired-job description, status 404 when the job is unknown, or status 400 for any
   *     other selector or state
   * @throws SchedulerException if the job cannot be fired
   */
  @WriteOperation
  public WebEndpointResponse<Object> triggerQuartzJob(
      @Selector String jobs, @Selector String group, @Selector String name, String state)
      throws SchedulerException {
    if ("jobs".equals(jobs) && "running".equals(state)) {
      return handleNull(this.delegate.triggerQuartzJob(group, name));
    }
    return new WebEndpointResponse<>(WebEndpointResponse.STATUS_BAD_REQUEST);
  }

  private <T> WebEndpointResponse<T> handle(
      String jobsOrTriggers, ResponseSupplier<T> jobAction, ResponseSupplier<T> triggerAction)
      throws SchedulerException {
    if ("jobs".equals(jobsOrTriggers)) {
      return handleNull(jobAction.get());
    }
    if ("triggers".equals(jobsOrTriggers)) {
      return handleNull(triggerAction.get());
    }
    return new WebEndpointResponse<>(WebEndpointResponse.STATUS_BAD_REQUEST);
  }

  private <T> WebEndpointResponse<T> handleNull(T value) {
    return value != null
        ? new WebEndpointResponse<>(value)
        : new WebEndpointResponse<>(WebEndpointResponse.STATUS_NOT_FOUND);
  }

  @FunctionalInterface
  private interface ResponseSupplier<T> {

    T get() throws SchedulerException;
  }

  /** Registers reflection hints for the response types that Jackson binds on the native image. */
  static class QuartzEndpointWebExtensionRuntimeHints implements RuntimeHintsRegistrar {

    private final BindingReflectionHintsRegistrar bindingRegistrar =
        new BindingReflectionHintsRegistrar();

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      this.bindingRegistrar.registerReflectionHints(
          hints.reflection(),
          QuartzGroupsDescriptor.class,
          QuartzJobDetailsDescriptor.class,
          QuartzJobGroupSummaryDescriptor.class,
          QuartzTriggerGroupSummaryDescriptor.class);
    }
  }
}
