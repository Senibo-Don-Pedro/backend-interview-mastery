public class StringInternalsTutorial {

    public static void main(String[] args) {

        // ── 1. Immutability — methods return new Strings ──────────────────────
        String name = "Temi";
        name.toUpperCase();                    // result thrown away
        System.out.println(name);             // "Temi" — unchanged

        String upper = name.toUpperCase();    // capture the new String
        System.out.println(upper);            // "TEMI"


        // ── 2. String pool — literals reuse same object ───────────────────────
        String a = "Temi";
        String b = "Temi";
        System.out.println(a == b);           // true — same object in pool
        System.out.println(a.equals(b));      // true — same content


        // ── 3. new String() — bypasses the pool ──────────────────────────────
        String c = new String("Temi");
        System.out.println(a == c);           // false — different objects
        System.out.println(a.equals(c));      // true — same content


        // ── 4. intern() — pull into pool manually ─────────────────────────────
        String d = new String("Temi").intern();
        System.out.println(a == d);           // true — d now points to pool object


        // ── 5. Why == is unreliable for Strings ──────────────────────────────
        // Same content, different == results depending on how created
        String s1 = "Hello";
        String s2 = "Hello";
        String s3 = new String("Hello");

        System.out.println(s1 == s2);         // true — pool
        System.out.println(s1 == s3);         // false — new object
        System.out.println(s1.equals(s3));    // true — always use equals()


        // ── 6. Concatenation in loops — performance trap ──────────────────────
        // BAD — creates new String every iteration
        String bad = "";
        for (int i = 0; i < 5; i++) {
            bad = bad + i;   // new String object each time
        }
        System.out.println(bad); // "01234"

        // GOOD — StringBuilder modifies same object
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(i);    // modifies same object
        }
        System.out.println(sb.toString()); // "01234" — same result, much more efficient


        // ── 7. Common String methods — all return new Strings ─────────────────
        String original = "  hello world  ";
        System.out.println(original.trim());           // "hello world" — new String
        System.out.println(original.replace("hello", "hi")); // "  hi world  " — new String
        System.out.println(original.contains("world")); // true
        System.out.println(original.toUpperCase());    // "  HELLO WORLD  " — new String
        System.out.println(original);                  // "  hello world  " — unchanged
    }
}
