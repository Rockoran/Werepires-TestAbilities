package pow.crimson2.libs.gson;

import java.lang.reflect.Type;

public interface InstanceCreator<T> {
   T createInstance(Type var1);
}
