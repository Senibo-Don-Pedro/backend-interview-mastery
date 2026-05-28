import java.util.*;

public class CollectionsTutorial {

    public static void main(String[] args) {

        // ── 1. ArrayList ─────────────────────────────────────────────────────
        // Resizable array. Fast reads, slow middle inserts.
        List<String> arrayList = new ArrayList<>();
        arrayList.add("Alice");
        arrayList.add("Bob");
        arrayList.add("Charlie");
        arrayList.add(0, "Zara");           // slow — shifts everything right
        System.out.println(arrayList);      // [Zara, Alice, Bob, Charlie]
        System.out.println(arrayList.get(2)); // fast — direct index access → Bob


        // ── 2. LinkedList ────────────────────────────────────────────────────
        // Chain of nodes. Fast middle inserts, slow reads by index.
        LinkedList<String> linkedList = new LinkedList<>();
        linkedList.add("Alice");
        linkedList.add("Bob");
        linkedList.add("Charlie");
        linkedList.addFirst("Zara");        // fast — just rewires pointers
        linkedList.addLast("David");        // fast — just rewires pointers
        System.out.println(linkedList);     // [Zara, Alice, Bob, Charlie, David]
        System.out.println(linkedList.get(2)); // slow — walks nodes to index 2


        // ── 3. HashMap ───────────────────────────────────────────────────────
        // Key-value pairs. Uses hashing for fast lookup.
        Map<String, Integer> ages = new HashMap<>();
        ages.put("Temi", 25);
        ages.put("Ada", 30);
        ages.put("John", 22);
        ages.put("Temi", 28);              // overwrites existing key "Temi"
        System.out.println(ages.get("Temi")); // 28 — not 25
        System.out.println(ages.get("Unknown")); // null — key doesn't exist

        // Safe get with default fallback
        int age = ages.getOrDefault("Unknown", 0);
        System.out.println(age);           // 0

        // Check if key exists
        System.out.println(ages.containsKey("Ada")); // true

        // Loop through all entries
        for (Map.Entry<String, Integer> entry : ages.entrySet()) {
            System.out.println(entry.getKey() + " is " + entry.getValue());
        }


        // ── 4. HashSet ───────────────────────────────────────────────────────
        // Unique elements only. Uses HashMap under the hood.
        Set<String> names = new HashSet<>();
        names.add("Temi");
        names.add("Ada");
        names.add("Temi");                 // ignored — already exists
        names.add("John");
        System.out.println(names.size());  // 3 — not 4, duplicate ignored
        System.out.println(names.contains("Ada")); // true — instant lookup


        // ── 5. ArrayList vs HashSet — contains() speed difference ────────────
        // ArrayList searches one by one — slow on large data
        // HashSet uses hashing — instant regardless of size
        List<String> listIds = new ArrayList<>(List.of("id1", "id2", "id3"));
        Set<String> setIds = new HashSet<>(List.of("id1", "id2", "id3"));

        System.out.println(listIds.contains("id3")); // true — searched through list
        System.out.println(setIds.contains("id3"));  // true — instant hash lookup


        // ── 6. HashMap collision demo ─────────────────────────────────────────
        // Two different keys, same bucket — Java handles with equals()
        // "Aa" and "BB" have the same hashCode in Java — classic example
        Map<String, String> collisionMap = new HashMap<>();
        collisionMap.put("Aa", "value1");
        collisionMap.put("BB", "value2");  // same hashCode as "Aa" — collision!
        System.out.println(collisionMap.get("Aa")); // value1 — found via equals()
        System.out.println(collisionMap.get("BB")); // value2 — found via equals()

    }
}
