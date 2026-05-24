package pow.crimson2.libs.gson;

public final class JsonSyntaxException extends JsonParseException {
   private static final long serialVersionUID = 1L;

   public JsonSyntaxException(String msg) {
      super(msg);
   }

   public JsonSyntaxException(String msg, Throwable cause) {
      super(msg, cause);
   }

   public JsonSyntaxException(Throwable cause) {
      super(cause);
   }
}
