package org.quartz;

import java.time.Instant;
import java.util.Calendar;

/**
 * Converts between {@link Instant} and the {@link Calendar} values still used inside cron and
 * calendar calculations.
 *
 * <p>This type is not a general-purpose clock. {@code null} inputs are preserved, except {@link
 * #toEpochMilli(Instant)}, which treats {@code null} as epoch millis {@code 0}.
 */
public final class Instants {

  private Instants() {}

  /**
   * Returns the current instant from the system clock.
   *
   * @return {@link Instant#now()}
   */
  public static Instant now() {
    return Instant.now();
  }

  /**
   * Returns the instant of the given epoch millis.
   *
   * @param epochMilli milliseconds since the epoch
   * @return the corresponding instant
   */
  public static Instant ofEpochMilli(long epochMilli) {
    return Instant.ofEpochMilli(epochMilli);
  }

  /**
   * Returns epoch millis for an instant.
   *
   * @param instant the instant, or {@code null}
   * @return {@code instant.toEpochMilli()}, or {@code 0} when {@code instant} is {@code null}
   */
  public static long toEpochMilli(Instant instant) {
    return instant == null ? 0L : instant.toEpochMilli();
  }

  /**
   * Reads an instant from a calendar.
   *
   * @param calendar the calendar, or {@code null}
   * @return {@code calendar.toInstant()}, or {@code null} when {@code calendar} is {@code null}
   */
  public static Instant fromCalendar(Calendar calendar) {
    return calendar == null ? null : calendar.toInstant();
  }

  /**
   * Sets a calendar to an instant. Does nothing when either argument is {@code null}.
   *
   * @param calendar the calendar to update
   * @param instant the instant to apply
   */
  public static void setCalendar(Calendar calendar, Instant instant) {
    if (calendar != null && instant != null) {
      calendar.setTimeInMillis(instant.toEpochMilli());
    }
  }

  /**
   * Adds a number of milliseconds to an instant.
   *
   * @param instant the starting instant, or {@code null}
   * @param millis milliseconds to add; may be negative
   * @return the shifted instant, or {@code null} when {@code instant} is {@code null}
   */
  public static Instant plusMillis(Instant instant, long millis) {
    return instant == null ? null : instant.plusMillis(millis);
  }
}
