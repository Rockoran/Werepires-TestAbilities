package pow.crimson2.libs.gson.internal.bind;

import pow.crimson2.libs.gson.TypeAdapter;

public abstract class SerializationDelegatingTypeAdapter<T> extends TypeAdapter<T> {
   public abstract TypeAdapter<T> getSerializationDelegate();
}
