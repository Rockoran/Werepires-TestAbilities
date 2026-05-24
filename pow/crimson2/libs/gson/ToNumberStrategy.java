package pow.crimson2.libs.gson;

import java.io.IOException;
import pow.crimson2.libs.gson.stream.JsonReader;

public interface ToNumberStrategy {
   Number readNumber(JsonReader var1) throws IOException;
}
