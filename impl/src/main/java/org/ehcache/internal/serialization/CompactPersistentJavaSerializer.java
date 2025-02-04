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

package org.ehcache.internal.serialization;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Map;
import org.ehcache.exceptions.SerializerException;
import org.ehcache.spi.serialization.Serializer;
import org.ehcache.spi.service.FileBasedPersistenceContext;

/**
 *
 * @author cdennis
 */
@Serializer.Persistent
public class CompactPersistentJavaSerializer<T> implements Serializer<T> {

  private final File stateFile;
  private final CompactJavaSerializer<T> serializer;
  
  public CompactPersistentJavaSerializer(ClassLoader classLoader, FileBasedPersistenceContext persistence) throws IOException, ClassNotFoundException {
    this.stateFile = new File(persistence.getDirectory(), "CompactPersistentJavaSerializer.state");
    if (stateFile.exists()) {
      serializer = new CompactJavaSerializer<T>(classLoader, readSerializationMappings(stateFile));
    } else {
      serializer = new CompactJavaSerializer<T>(classLoader);
    }
  }

  @Override
  public final void close() throws IOException {
    try {
      writeSerializationMappings(stateFile, serializer.getSerializationMappings());
    } finally {
      serializer.close();
    }
  }

  private static Map<Integer, ObjectStreamClass> readSerializationMappings(File stateFile) throws IOException, ClassNotFoundException {
    FileInputStream fin = new FileInputStream(stateFile);
    try {
      ObjectInputStream oin = new ObjectInputStream(fin);
      try {
        return (Map<Integer, ObjectStreamClass>) oin.readObject();
      } finally {
        oin.close();
      }
    } finally {
      fin.close();
    }
  }
  
  private static void writeSerializationMappings(File stateFile, Map<Integer, ObjectStreamClass> mappings) throws IOException {
    OutputStream fout = new FileOutputStream(stateFile);
    try {
      ObjectOutputStream oout = new ObjectOutputStream(fout);
      try {
        oout.writeObject(mappings);
      } finally {
        oout.close();
      }
    } finally {
      fout.close();
    }
  }

  @Override
  public ByteBuffer serialize(T object) throws SerializerException {
    return serializer.serialize(object);
  }

  @Override
  public T read(ByteBuffer binary) throws ClassNotFoundException, SerializerException {
    return serializer.read(binary);
  }

  @Override
  public boolean equals(T object, ByteBuffer binary) throws ClassNotFoundException, SerializerException {
    return serializer.equals(object, binary);
  }
  
}
