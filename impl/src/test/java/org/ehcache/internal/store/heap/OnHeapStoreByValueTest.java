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
package org.ehcache.internal.store.heap;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.CacheManagerBuilder;
import org.ehcache.config.CacheConfigurationBuilder;
import org.ehcache.config.Eviction;
import org.ehcache.config.EvictionVeto;
import org.ehcache.config.EvictionPrioritizer;
import org.ehcache.config.ResourcePools;
import org.ehcache.config.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.exceptions.SerializerException;
import org.ehcache.expiry.Expirations;
import org.ehcache.expiry.Expiry;
import org.ehcache.internal.SystemTimeSource;
import org.ehcache.internal.TimeSource;
import org.ehcache.internal.serialization.JavaSerializer;
import org.ehcache.internal.store.heap.service.OnHeapStoreServiceConfiguration;
import org.ehcache.spi.cache.Store;
import org.ehcache.spi.cache.Store.ValueHolder;
import org.junit.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.ehcache.config.ResourcePoolsBuilder.newResourcePoolsBuilder;
import org.ehcache.spi.serialization.Serializer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.fail;

@SuppressWarnings("serial")
public class OnHeapStoreByValueTest extends BaseOnHeapStoreTest {

  @Test
  public void testPutNotSerializableValue() throws Exception {
    OnHeapStore<Serializable, Serializable> store = newStore();
    try {
      store.put("key1", new ArrayList<Object>() {{ add(new Object()); }});
      fail();
    } catch (SerializerException se) {
      // expected
    }
  }
  
  @Test
  public void testPutNotSerializableKey() throws Exception {
    OnHeapStore<Serializable, Serializable> store = newStore();
    try {
      store.put(new ArrayList<Object>() {{ add(new Object()); }}, "value");
      fail();
    } catch (SerializerException se) {
      // expected
    }
  }

  @Test
  public void testValueUniqueObject()  throws Exception {
    OnHeapStore<Serializable, Serializable> store = newStore();
    
    String key = "key";
    List<String> value = new ArrayList<String>();
    value.add("value");
    
    store.put(key, (Serializable) value);
    
    // mutate the value -- should not affect cache
    value.clear();
    
    ValueHolder<Serializable> valueHolder = store.get(key);
    if (valueHolder.value() == value || ! valueHolder.value().equals(Collections.singletonList("value"))) {
      throw new AssertionError();
    }
  }
  
  @Test
  public void testKeyUniqueObject() throws Exception {
    OnHeapStore<Serializable, Serializable> store = newStore();
   
    List<String> key = new ArrayList<String>();
    key.add("key");
    String value = "value";
    
    store.put((Serializable) key, value);
    
    // mutate the key -- should not affect cache
    key.clear();
    
    Serializable storeKey = store.iterator().next().getKey();
    if (storeKey == key || ! storeKey.equals(Collections.singletonList("key"))) {
      throw new AssertionError();
    }
  }

  @Test
  public void testStoreByValue() {
    CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build(false);
    cacheManager.init();

    final Cache<Long, String> cache1 = cacheManager.createCache("cache1",
        CacheConfigurationBuilder.newCacheConfigurationBuilder().withResourcePools(ResourcePoolsBuilder.newResourcePoolsBuilder().heap(1, EntryUnit.ENTRIES))
            .buildConfig(Long.class, String.class));
    performAssertions(cache1, true);

    final Cache<Long, String> cache2 = cacheManager.createCache("cache2",
        CacheConfigurationBuilder.newCacheConfigurationBuilder().add(new OnHeapStoreServiceConfiguration().storeByValue(true))
            .buildConfig(Long.class, String.class));
    performAssertions(cache2, false);

    final Cache<Long, String> cache3 = cacheManager.createCache("cache3",
        CacheConfigurationBuilder.newCacheConfigurationBuilder().add(new OnHeapStoreServiceConfiguration().storeByValue(false))
            .buildConfig(Long.class, String.class));
    performAssertions(cache3, true);

    cacheManager.close();
  }

  @Override
  protected <K, V> OnHeapStore<K, V> newStore() {
    return newStore(SystemTimeSource.INSTANCE, Expirations.noExpiration(), Eviction.none());
  }

  @Override
  protected <K, V> OnHeapStore<K, V> newStore(EvictionVeto<? super K, ? super V> veto) {
    return newStore(SystemTimeSource.INSTANCE, Expirations.noExpiration(), veto);
  }

  @Override
  protected <K, V> OnHeapStore<K, V> newStore(TimeSource timeSource, Expiry<? super K, ? super V> expiry) {
    return newStore(timeSource, expiry, Eviction.none());
  }

  @Override
  protected <K, V> OnHeapStore<K, V> newStore(final TimeSource timeSource,
      final Expiry<? super K, ? super V> expiry, final EvictionVeto<? super K, ? super V> veto) {
    return new OnHeapStore<K, V>(new Store.Configuration<K, V>() {
      
      @SuppressWarnings("unchecked")
      @Override
      public Class<K> getKeyType() {
        return (Class<K>) Serializable.class;
      }

      @SuppressWarnings("unchecked")
      @Override
      public Class<V> getValueType() {
        return (Class<V>) Serializable.class;
      }

      @Override
      public EvictionVeto<? super K, ? super V> getEvictionVeto() {
        return veto;
      }

      @Override
      public EvictionPrioritizer<? super K, ? super V> getEvictionPrioritizer() {
        return Eviction.Prioritizer.LRU;
      }

      @Override
      public ClassLoader getClassLoader() {
        return getClass().getClassLoader();
      }

      @Override
      public Expiry<? super K, ? super V> getExpiry() {
        return expiry;
      }

      @Override
      public ResourcePools getResourcePools() {
        return newResourcePoolsBuilder().heap(100, EntryUnit.ENTRIES).build();
      }

      @Override
      public Serializer<K> getKeySerializer() {
        return new JavaSerializer<K>(getClass().getClassLoader());
      }

      @Override
      public Serializer<V> getValueSerializer() {
        return new JavaSerializer<V>(getClass().getClassLoader());
      }
    }, timeSource, true);
  }

  private void performAssertions(Cache<Long, String> cache, boolean same) {
    cache.put(1L, "one");
    String s1 = cache.get(1L);
    String s2 = cache.get(1L);
    String s3 = cache.get(1L);

    assertThat(s1 == s2, is(same));
    assertThat(s2 == s3, is(same));
  }
}
