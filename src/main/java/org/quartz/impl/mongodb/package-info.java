/**
 * MongoDB {@link org.quartz.spi.JobStore} for this scheduler.
 *
 * <p>{@link org.quartz.impl.mongodb.MongoJobStore} persists jobs, triggers, and calendars as BSON
 * documents. {@link org.quartz.impl.mongodb.BsonJobStoreCodec} is the package-private mapping and
 * does not use Java serialization.
 */
package org.quartz.impl.mongodb;
