package pow.crimson2.libs.gson.internal;

import java.io.IOException;
import pow.crimson2.libs.gson.stream.JsonReader;

public abstract class JsonReaderInternalAccess {
   public static JsonReaderInternalAccess INSTANCE;

   public abstract void promoteNameToValue(JsonReader var1) throws IOException;
}
