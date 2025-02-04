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
package com.pany.ehcache.serializer;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.ehcache.exceptions.SerializerException;
import org.ehcache.internal.serialization.CompactJavaSerializer;

import org.ehcache.spi.serialization.Serializer;

@Serializer.Transient
public class TestSerializer<T> implements Serializer<T> {
  
  private final Serializer<T> serializer;

  public TestSerializer(ClassLoader classLoader) {
    serializer = new CompactJavaSerializer<T>(classLoader);
  }

  @Override
  public ByteBuffer serialize(T object) throws SerializerException {
    return serializer.serialize(object);
  }

  @Override
  public T read(ByteBuffer binary) throws SerializerException, ClassNotFoundException {
    return serializer.read(binary);
  }

  @Override
  public boolean equals(T object, ByteBuffer binary) throws SerializerException, ClassNotFoundException {
    return serializer.equals(object, binary);
  }

  @Override
  public void close() throws IOException {
    serializer.close();
  }
}
