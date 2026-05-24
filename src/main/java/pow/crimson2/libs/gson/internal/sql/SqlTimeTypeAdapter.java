package pow.crimson2.libs.gson.internal.sql;

import java.io.IOException;
import java.sql.Time;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import pow.crimson2.libs.gson.Gson;
import pow.crimson2.libs.gson.JsonSyntaxException;
import pow.crimson2.libs.gson.TypeAdapter;
import pow.crimson2.libs.gson.TypeAdapterFactory;
import pow.crimson2.libs.gson.reflect.TypeToken;
import pow.crimson2.libs.gson.stream.JsonReader;
import pow.crimson2.libs.gson.stream.JsonToken;
import pow.crimson2.libs.gson.stream.JsonWriter;

final class SqlTimeTypeAdapter extends TypeAdapter<Time> {
   static final TypeAdapterFactory FACTORY = new TypeAdapterFactory() {
      @Override
      public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
         return typeToken.getRawType() == Time.class ? new SqlTimeTypeAdapter() : null;
      }
   };
   private final DateFormat format = new SimpleDateFormat("hh:mm:ss a");

   private SqlTimeTypeAdapter() {
   }

   public Time read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
         in.nextNull();
         return null;
      }

      String s = in.nextString();

      try {
         synchronized (this) {
            Date date = this.format.parse(s);
            return new Time(date.getTime());
         }
      } catch (ParseException e) {
         throw new JsonSyntaxException("Failed parsing '" + s + "' as SQL Time; at path " + in.getPreviousPath(), e);
      }
   }

   public void write(JsonWriter out, Time value) throws IOException {
      if (value == null) {
         out.nullValue();
      } else {
         String timeString;
         synchronized (this) {
            timeString = this.format.format(value);
         }

         out.value(timeString);
      }
   }
}
