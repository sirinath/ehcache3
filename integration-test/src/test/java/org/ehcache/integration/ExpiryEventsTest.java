/*
 * Copyright Terracotta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ehcache.integration;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.CacheManagerBuilder;
import org.ehcache.config.CacheConfigurationBuilder;
import org.ehcache.config.ResourcePoolsBuilder;
import org.ehcache.config.persistence.CacheManagerPersistenceConfiguration;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.event.CacheEvent;
import org.ehcache.event.CacheEventListener;
import org.ehcache.event.EventFiring;
import org.ehcache.event.EventOrdering;
import org.ehcache.event.EventType;
import org.ehcache.expiry.Duration;
import org.ehcache.expiry.Expirations;
import org.ehcache.internal.TimeSourceConfiguration;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

/**
 * Created by alsu on 06/08/15.
 */
public class ExpiryEventsTest {

  private static final ResourcePoolsBuilder resourcePoolsBuilder =
      ResourcePoolsBuilder.newResourcePoolsBuilder().heap(3, EntryUnit.ENTRIES);

  private static final CacheConfigurationBuilder<Long, String> cacheConfigBuilder =
      CacheConfigurationBuilder.<Long, String>newCacheConfigurationBuilder()
          .withExpiry(Expirations.timeToLiveExpiration(new Duration(1, TimeUnit.SECONDS)));;

  private static final TestTimeSource testTimeSource = new TestTimeSource();

  private CacheManager cacheManager;

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  @Before
  public void setup() throws IOException {
    cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
        .with(new CacheManagerPersistenceConfiguration(folder.newFolder("tempData")))
        .using(new TimeSourceConfiguration(testTimeSource))
        .build(true);
    testTimeSource.setTimeMillis(0);
  }

  @After
  public void tearDown() {
    if (cacheManager != null) {
      cacheManager.close();
    }
  }

  @Test
  public void testExpiredEventsOnHeap() throws Exception {

    cacheConfigBuilder.withResourcePools(resourcePoolsBuilder);
    Cache<Long, String> testCache = cacheManager.createCache("onHeapCache",
        cacheConfigBuilder.buildConfig(Long.class, String.class));

    performActualTest(testCache);
 }

  @Test
  public void testExpiredEventsOnHeapAndOffHeap() throws Exception {

    CacheConfigurationBuilder<Long, String> configBuilder = cacheConfigBuilder.withResourcePools(
        resourcePoolsBuilder.offheap(1, MemoryUnit.MB));
    Cache<Long, String> testCache = cacheManager.createCache("onHeapOffHeapCache",
        configBuilder.buildConfig(Long.class, String.class));

    performActualTest(testCache);
  }

  @Test
  public void testExpiredEventsOnHeapAndDisk() throws Exception {

    CacheConfigurationBuilder<Long, String> configBuilder = cacheConfigBuilder.withResourcePools(
        resourcePoolsBuilder.disk(1, MemoryUnit.MB));
    Cache<Long, String> testCache = cacheManager.createCache("onHeapDiskCache",
        configBuilder.buildConfig(Long.class, String.class));

    performActualTest(testCache);
  }

  @Test
  public void testExpiredEventsOnHeapAndOffHeapAndDisk() throws Exception {

    CacheConfigurationBuilder<Long, String> configBuilder = cacheConfigBuilder.withResourcePools(
        resourcePoolsBuilder.offheap(1, MemoryUnit.MB).disk(2, MemoryUnit.MB));
    Cache<Long, String> testCache = cacheManager.createCache("onHeapOffHeapDiskCache",
        configBuilder.buildConfig(Long.class, String.class));

    performActualTest(testCache);
  }

  private void performActualTest(Cache<Long, String> testCache) {

    final List<Long> expiredKeys = new CopyOnWriteArrayList<Long>();

    testCache.getRuntimeConfiguration().registerCacheEventListener(new CacheEventListener<Long, String>() {
      @Override
      public void onEvent(CacheEvent<Long, String> event) {
        expiredKeys.add(event.getKey());
      }
    }, EventOrdering.ORDERED, EventFiring.SYNCHRONOUS, EnumSet.of(EventType.EXPIRED));

    testCache.put(1L, "one");
    testCache.put(2L, "two");
    testCache.put(3L, "three");
    testCache.put(4L, "four");
    testCache.put(5L, "five");
    testCache.put(6L, "six");
    testCache.put(7L, "seven");

    testCache.get(1L);
    testCache.get(2L);
    testCache.get(3L);
    testCache.get(4L);
    testCache.get(5L);

    testTimeSource.setTimeMillis(1100);

    testCache.get(1L);
    testCache.get(2L);
    testCache.get(3L);
    testCache.get(4L);
    testCache.get(5L);
    testCache.get(6L);
    testCache.get(7L);

    assertThat(expiredKeys, containsInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L));
  }

}
