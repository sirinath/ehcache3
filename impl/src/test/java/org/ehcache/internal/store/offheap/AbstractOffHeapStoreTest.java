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

package org.ehcache.internal.store.offheap;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.ehcache.Cache;
import org.ehcache.config.EvictionVeto;
import org.ehcache.events.StoreEventListener;
import org.ehcache.exceptions.CacheAccessException;
import org.ehcache.expiry.Duration;
import org.ehcache.expiry.Expirations;
import org.ehcache.expiry.Expiry;
import org.ehcache.function.Function;
import org.ehcache.internal.TimeSource;
import org.ehcache.spi.cache.AbstractValueHolder;
import org.ehcache.spi.cache.Store;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import org.hamcrest.MatcherAssert;
import org.ehcache.spi.cache.tiering.CachingTier;
import org.junit.Test;

/**
 *
 * @author cdennis
 */
public abstract class AbstractOffHeapStoreTest {

  @Test
  public void testGetAndRemove() throws Exception {
    TestTimeSource timeSource = new TestTimeSource();
    AbstractOffHeapStore<String, String> offHeapStore = createAndInitStore(timeSource, Expirations.timeToIdleExpiration(new Duration(15L, TimeUnit.MILLISECONDS)));

    try {
      assertThat(offHeapStore.getAndRemove("1"), is(nullValue()));

      offHeapStore.put("1", "one");

      assertThat(offHeapStore.getAndRemove("1").value(), equalTo("one"));
      assertThat(offHeapStore.getAndRemove("1"), is(nullValue()));

      offHeapStore.put("1", "one");
      timeSource.advanceTime(20);
      assertThat(offHeapStore.getAndRemove("1"), is(nullValue()));
    } finally {
      destroyStore(offHeapStore);
    }
  }

  @Test
  public void testGetAndRemoveExpiredElementReturnsNull() throws Exception {
    TestTimeSource timeSource = new TestTimeSource();
    AbstractOffHeapStore<String, String> offHeapStore = createAndInitStore(timeSource, Expirations.timeToIdleExpiration(new Duration(15L, TimeUnit.MILLISECONDS)));

    try {
      assertThat(offHeapStore.getAndRemove("1"), is(nullValue()));

      offHeapStore.put("1", "one");

      final AtomicReference<Store.ValueHolder<String>> invalidated = new AtomicReference<Store.ValueHolder<String>>();
      offHeapStore.setInvalidationListener(new CachingTier.InvalidationListener<String, String>() {
        @Override
        public void onInvalidation(String key, Store.ValueHolder<String> valueHolder) {
          invalidated.set(valueHolder);
        }
      });

      timeSource.advanceTime(20);
      assertThat(offHeapStore.getAndRemove("1"), is(nullValue()));
      assertThat(invalidated.get().value(), equalTo("one"));
      assertThat(invalidated.get().isExpired(timeSource.getTimeMillis(), TimeUnit.MILLISECONDS), is(true));
    } finally {
      destroyStore(offHeapStore);
    }
  }

  @Test
  public void testGetOrComputeIfAbsent() throws Exception {
    final TestTimeSource timeSource = new TestTimeSource();
    AbstractOffHeapStore<String, String> offHeapStore = createAndInitStore(timeSource, Expirations.timeToIdleExpiration(new Duration(15L, TimeUnit.MILLISECONDS)));

    try {
      assertThat(offHeapStore.getOrComputeIfAbsent("1", new Function<String, Store.ValueHolder<String>>() {
        @Override
        public Store.ValueHolder<String> apply(String key) {
          return new SimpleValueHolder<String>("one", timeSource.getTimeMillis(), 15);
        }
      }).value(), equalTo("one"));

      timeSource.advanceTime(20);

      try {
        offHeapStore.getOrComputeIfAbsent("1", new Function<String, Store.ValueHolder<String>>() {
          @Override
          public Store.ValueHolder<String> apply(String key) {
            return new SimpleValueHolder<String>("un", timeSource.getTimeMillis(), 15);
          }
        });
        fail("expected AssertionError");
      } catch (AssertionError ae) {
        // expected
      }
    } finally {
      destroyStore(offHeapStore);
    }
  }

  @Test
  public void testInvalidateKey() throws Exception {
    final TestTimeSource timeSource = new TestTimeSource();
    AbstractOffHeapStore<String, String> offHeapStore = createAndInitStore(timeSource, Expirations.timeToIdleExpiration(new Duration(15L, TimeUnit.MILLISECONDS)));

    try {
      final AtomicReference<Store.ValueHolder<String>> invalidated = new AtomicReference<Store.ValueHolder<String>>();
      offHeapStore.setInvalidationListener(new CachingTier.InvalidationListener<String, String>() {
        @Override
        public void onInvalidation(String key, Store.ValueHolder<String> valueHolder) {
          invalidated.set(valueHolder);
        }
      });

      offHeapStore.invalidate("1");
      assertThat(invalidated.get(), is(nullValue()));

      offHeapStore.put("1", "one");
      offHeapStore.invalidate("1");
      assertThat(invalidated.get().value(), equalTo("one"));

      assertThat(offHeapStore.get("1"), is(nullValue()));
    } finally {
      destroyStore(offHeapStore);
    }
  }

  @Test
  public void testInvalidateAll() throws Exception {
    final TestTimeSource timeSource = new TestTimeSource();
    AbstractOffHeapStore<String, String> offHeapStore = createAndInitStore(timeSource, Expirations.timeToIdleExpiration(new Duration(15L, TimeUnit.MILLISECONDS)));

    try {
      offHeapStore.put("1", "one");
      offHeapStore.put("2", "two");
      offHeapStore.put("3", "three");
      offHeapStore.clear();

      assertThat(offHeapStore.get("1"), is(nullValue()));
      assertThat(offHeapStore.get("2"), is(nullValue()));
      assertThat(offHeapStore.get("3"), is(nullValue()));
    } finally {
      destroyStore(offHeapStore);
    }
  }

  @Test
  public void testWriteBackOfValueHolder() throws CacheAccessException {
    TestTimeSource timeSource = new TestTimeSource();
    AbstractOffHeapStore<String, String> offHeapStore = createAndInitStore(timeSource, Expirations.timeToIdleExpiration(new Duration(15L, TimeUnit.MILLISECONDS)));
    try {
      offHeapStore.put("key1", "value1");
      timeSource.advanceTime(10);
      OffHeapValueHolder<String> valueHolder = (OffHeapValueHolder<String>)offHeapStore.get("key1");
      assertThat(valueHolder.lastAccessTime(TimeUnit.MILLISECONDS), is(10L));
      timeSource.advanceTime(10);
      assertThat(offHeapStore.get("key1"), notNullValue());
      timeSource.advanceTime(16);
      assertThat(offHeapStore.get("key1"), nullValue());
    } finally {
      destroyStore(offHeapStore);
    }
  }

  @Test
  public void testEvictionVeto() throws CacheAccessException {
    TestTimeSource timeSource = new TestTimeSource();
    Expiry<Object, Object> expiry = Expirations.timeToIdleExpiration(new Duration(15L, TimeUnit.MILLISECONDS));
    EvictionVeto<String, byte[]> evictionVeto = new EvictionVeto<String, byte[]>() {

      @Override
      public boolean test(Cache.Entry<String, byte[]> entry) {
//        return entry.getKey().equals("key3");
        return true;
      }
    };
    AbstractOffHeapStore<String, byte[]> offHeapStore = createAndInitStore(timeSource, expiry, evictionVeto);
    try {
      offHeapStore.enableStoreEventNotifications(new TestStoreEventListener<String, byte[]>());

      int valueLength = 200000;
      byte[] value = new byte[valueLength];
      value[0] = 1;
      value[valueLength/2] = 1;
      value[valueLength - 1] = 1;
      offHeapStore.put("key1", value);
      offHeapStore.put("key2", value);
      offHeapStore.put("key3", value);
      offHeapStore.put("key4", value);
      offHeapStore.put("key5", value);
      offHeapStore.put("key6", value);
      //offHeapStore.put("key7", value);
    } finally {
      destroyStore(offHeapStore);
    }

  }

  @Test
  public void testFlushUpdatesAccessStats() throws CacheAccessException {
    final TestTimeSource timeSource = new TestTimeSource();
    final Expiry<Object, Object> expiry = Expirations.timeToIdleExpiration(new Duration(15L, TimeUnit.MILLISECONDS));
    final AbstractOffHeapStore<String, String> store = createAndInitStore(timeSource, expiry);
    try {
      final String key = "foo";
      final String value = "bar";
      store.put(key, value);
      final Store.ValueHolder<String> firstValueHolder = store.getAndFault(key);
      store.put(key, value);
      final Store.ValueHolder<String> secondValueHolder = store.getAndFault(key);
      timeSource.advanceTime(10);
      ((AbstractValueHolder)firstValueHolder).accessed(timeSource.getTimeMillis(), expiry.getExpiryForAccess(key, value));
      timeSource.advanceTime(10);
      ((AbstractValueHolder)secondValueHolder).accessed(timeSource.getTimeMillis(), expiry.getExpiryForAccess(key, value));
      assertThat(store.flush(key, new DelegatingValueHolder<String>(firstValueHolder)), is(false));
      assertThat(store.flush(key, new DelegatingValueHolder<String>(secondValueHolder)), is(true));
      timeSource.advanceTime(10); // this should NOT affect
      assertThat(store.getAndFault(key).lastAccessTime(TimeUnit.MILLISECONDS), is(secondValueHolder.creationTime(TimeUnit.MILLISECONDS) + 20));
    } finally {
      destroyStore(store);
    }
  }

  @Test
  public void testFlushUpdatesHits() throws CacheAccessException {
    final TestTimeSource timeSource = new TestTimeSource();
    final AbstractOffHeapStore<String, String> store = createAndInitStore(timeSource, Expirations.noExpiration());
    final String key = "foo1";
    final String value = "bar1";
    store.put(key, value);
    for(int i = 0; i < 5; i++) {
      final Store.ValueHolder<String> valueHolder = store.getAndFault(key);
      timeSource.advanceTime(1);
      ((AbstractValueHolder)valueHolder).accessed(timeSource.getTimeMillis(), new Duration(1L, TimeUnit.MILLISECONDS));
      assertThat(store.flush(key, new DelegatingValueHolder<String>(valueHolder)), is(true));
    }
    assertThat(store.getAndFault(key).hits(), is(5l));
  }

  @Test
  public void testExpiryEventFiredOnExpiredCachedEntry() throws CacheAccessException {
    TestTimeSource timeSource = new TestTimeSource();
    AbstractOffHeapStore<String, String> offHeapStore = createAndInitStore(timeSource, Expirations.timeToIdleExpiration(new Duration(10L, TimeUnit.MILLISECONDS)));
    try {
      final List<String> expiredKeys = new ArrayList<String>();
      offHeapStore.enableStoreEventNotifications(new StoreEventListener<String, String>() {

        @Override
        public void onEviction(final String key, final Store.ValueHolder<String> valueHolder) {
          throw new AssertionError("This should not have happened.");
        }

        @Override
        public void onExpiration(final String key, final Store.ValueHolder<String> valueHolder) {
          expiredKeys.add(key);
        }
      });

      offHeapStore.put("key1", "value1");
      offHeapStore.put("key2", "value2");

      offHeapStore.get("key1");   // Bring the entry to the caching tier

      timeSource.advanceTime(11);   // Expire the elements

      offHeapStore.get("key1");
      offHeapStore.get("key2");
      MatcherAssert.assertThat(expiredKeys, containsInAnyOrder("key1", "key2"));
    } finally {
      destroyStore(offHeapStore);
    }
  }

  protected abstract AbstractOffHeapStore<String, String> createAndInitStore(final TimeSource timeSource, final Expiry<? super String, ? super String> expiry);

  protected abstract AbstractOffHeapStore<String, byte[]> createAndInitStore(final TimeSource timeSource, final Expiry<? super String, ? super byte[]> expiry, EvictionVeto<? super String, ? super byte[]> evictionVeto);

  protected abstract void destroyStore(AbstractOffHeapStore<?, ?> store);
  
  private static class TestStoreEventListener<K, V> implements StoreEventListener<K, V> {

    @Override
    public void onEviction(final K key, final Store.ValueHolder<V> valueHolder) {
      System.out.println("Evicted " + key);
    }

    @Override
    public void onExpiration(final K key, final Store.ValueHolder<V> valueHolder) {
      System.out.println("Expired " + key);
    }
  }

  private static class TestTimeSource implements TimeSource {

    private long time = 0;

    @Override
    public long getTimeMillis() {
      return time;
    }

    public void advanceTime(long step) {
      time += step;
    }
  }

  public static class DelegatingValueHolder<T> implements Store.ValueHolder<T> {

    private final Store.ValueHolder<T> valueHolder;

    public DelegatingValueHolder(final Store.ValueHolder<T> valueHolder) {
      this.valueHolder = valueHolder;
    }

    @Override
    public T value() {
      return valueHolder.value();
    }

    @Override
    public long creationTime(final TimeUnit unit) {
      return valueHolder.creationTime(unit);
    }

    @Override
    public long expirationTime(final TimeUnit unit) {
      return valueHolder.expirationTime(unit);
    }

    @Override
    public boolean isExpired(final long expirationTime, final TimeUnit unit) {
      return valueHolder.isExpired(expirationTime, unit);
    }

    @Override
    public long lastAccessTime(final TimeUnit unit) {
      return valueHolder.lastAccessTime(unit);
    }

    @Override
    public float hitRate(final long now, final TimeUnit unit) {
      return valueHolder.hitRate(now, unit);
    }

    @Override
    public long hits() {
      return valueHolder.hits();
    }

    @Override
    public long getId() {
      return valueHolder.getId();
    }
  }

  static class SimpleValueHolder<T> extends AbstractValueHolder<T> {

    private final T value;

    public SimpleValueHolder(T v, long creationTime, long expirationTime) {
      super(-1, creationTime, expirationTime);
      this.value = v;
    }

    @Override
    protected TimeUnit nativeTimeUnit() {
      return TimeUnit.MILLISECONDS;
    }

    @Override
    public T value() {
      return value;
    }

    @Override
    public long creationTime(TimeUnit unit) {
      return 0;
    }

    @Override
    public long expirationTime(TimeUnit unit) {
      return 0;
    }

    @Override
    public boolean isExpired(long expirationTime, TimeUnit unit) {
      return false;
    }

    @Override
    public long lastAccessTime(TimeUnit unit) {
      return 0;
    }

    @Override
    public float hitRate(long now, TimeUnit unit) {
      return 0;
    }

    @Override
    public long hits() {
      return 0;
    }

    @Override
    public long getId() {
      return 0;
    }
  }
}
