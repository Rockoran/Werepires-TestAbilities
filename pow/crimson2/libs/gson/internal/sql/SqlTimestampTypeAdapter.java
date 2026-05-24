package pow.crimson2.libs.gson.internal.sql;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Date;
import pow.crimson2.libs.gson.Gson;
import pow.crimson2.libs.gson.TypeAdapter;
import pow.crimson2.libs.gson.TypeAdapterFactory;
import pow.crimson2.libs.gson.reflect.TypeToken;
import pow.crimson2.libs.gson.stream.JsonReader;
import pow.crimson2.libs.gson.stream.JsonWriter;

class SqlTimestampTypeAdapter extends TypeAdapter<Timestamp> {
   static final TypeAdapterFactory FACTORY = new TypeAdapterFactory() {
      @Override
      public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
         if (typeToken.getRawType() == Timestamp.class) {
            TypeAdapter<Date> dateTypeAdapter = gson.getAdapter(Date.class);
            return new SqlTimestampTypeAdapter(dateTypeAdapter);
         } else {
            return null;
         }
      }
   };
   private final TypeAdapter<Date> dateTypeAdapter;

   private SqlTimestampTypeAdapter(TypeAdapter<Date> dateTypeAdapter) {
      this.dateTypeAdapter = dateTypeAdapter;
   }

   public Timestamp read(JsonReader in) throws IOException {
      Date date = this.dateTypeAdapter.read(in);
      return date != null ? new Timestamp(date.getTime()) : null;
   }

   public void write(JsonWriter out, Timestamp value) throws IOException {
      this.dateTypeAdapter.write(out, value);
   }
}
