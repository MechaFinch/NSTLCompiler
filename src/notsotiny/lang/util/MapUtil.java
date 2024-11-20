package notsotiny.lang.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapUtil {
    /**
     * Get the given map, or crate it
     * @param <K1>
     * @param <K2>
     * @param <V>
     * @param map
     * @param key
     * @return
     */
    public static <K1, K2, V> Map<K2, V> getOrCreateMap(Map<K1, Map<K2, V>> map, K1 key) {
        if(map.containsKey(key)) {
            return map.get(key);
        } else {
            Map<K2, V> submap = new HashMap<K2, V>();
            map.put(key, submap);
            return submap;
        }
    }
    
    /**
     * 
     * @param <K>
     * @param <V>
     * @param map
     * @param key
     * @return
     */
    public static <K, V> List<V> getOrCreateList(Map<K, List<V>> map, K key) {
        if(map.containsKey(key)) {
            return map.get(key);
        } else {
            List<V> list = new ArrayList<V>();
            map.put(key, list);
            return list;
        }
    }
}
