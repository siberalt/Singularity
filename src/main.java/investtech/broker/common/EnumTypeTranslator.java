package investtech.broker.common;

import java.util.Map;
import java.util.stream.Collectors;

public abstract class EnumTypeTranslator<V extends Enum<V>, E extends Enum<E>> {
    Map<V, E> typesMapping;
    Map<E, V> reversedTypesMapping;

    protected abstract Map<V, E> createMapping();

    public E to(V to) {
        if (null == typesMapping) {
            typesMapping = createMapping();
        }

        return typesMapping.get(to);
    }

    public V from(E from) {
        if (null == reversedTypesMapping) {
            if (null == typesMapping) {
                typesMapping = createMapping();
            }

            reversedTypesMapping = typesMapping.entrySet()
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
        }

        return reversedTypesMapping.get(from);
    }
}
