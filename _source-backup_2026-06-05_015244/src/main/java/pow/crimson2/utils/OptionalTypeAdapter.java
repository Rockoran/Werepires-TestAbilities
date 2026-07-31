package pow.crimson2.utils;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public class OptionalTypeAdapter implements TypeAdapterFactory {
   @Override
   public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
      Type type = typeToken.getType();
      if (typeToken.getRawType() != Optional.class) {
         return null;
      }

      Type wrappedType = ((ParameterizedType)type).getActualTypeArguments()[0];
      final TypeAdapter<?> wrappedAdapter = gson.getAdapter(TypeToken.get(wrappedType));
      return (TypeAdapter<T>) new TypeAdapter<Optional<?>>() {
         public void write(JsonWriter out, Optional<?> value) throws IOException {
            if (value != null && value.isPresent()) {
               ((TypeAdapter<Object>)wrappedAdapter).write(out, value.get());
            } else {
               out.nullValue();
            }
         }

         public Optional<?> read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
               in.nextNull();
               return Optional.empty();
            } else {
               return Optional.ofNullable(wrappedAdapter.read(in));
            }
         }
      };
   }
}
