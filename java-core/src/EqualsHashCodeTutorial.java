import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class EqualsHashCodeTutorial {

    public static void main(String[] args) {

        // ── 1. Default equals() — the problem ────────────────────────────────
        // No equals/hashCode override — uses memory address comparison
        BrokenAccount b1 = new BrokenAccount("001", "Temi");
        BrokenAccount b2 = new BrokenAccount("001", "Temi");
        System.out.println(b1.equals(b2)); // false — different objects in memory
        System.out.println(b1 == b2);      // false — definitely different objects


        // ── 2. Override equals only — silent HashMap bug ──────────────────────
        // equals says true, but hashCode still uses memory address
        // so they land in different buckets — get() returns null
        HalfFixedAccount h1 = new HalfFixedAccount("001", "Temi");
        HalfFixedAccount h2 = new HalfFixedAccount("001", "Temi");
        System.out.println(h1.equals(h2)); // true — equals is fixed

        Map<HalfFixedAccount, Double> halfMap = new HashMap<>();
        halfMap.put(h1, 5000.0);
        System.out.println(halfMap.get(h2)); // null ← silent bug, different hashCodes


        // ── 3. Override both equals AND hashCode — correct ────────────────────
        BankAccount a1 = new BankAccount("001", "Temi");
        BankAccount a2 = new BankAccount("001", "Temi");
        System.out.println(a1.equals(a2)); // true
        System.out.println(a1.hashCode() == a2.hashCode()); // true — same bucket

        Map<BankAccount, Double> balances = new HashMap<>();
        balances.put(a1, 5000.0);
        System.out.println(balances.get(a2)); // 5000.0 — found correctly


        // ── 4. HashSet deduplication — only works with proper hashCode ─────────
        Set<BankAccount> accounts = new HashSet<>();
        accounts.add(a1);
        accounts.add(a2); // same account — should be ignored
        System.out.println(accounts.size()); // 1 — duplicate correctly rejected


        // ── 5. == vs equals() on Strings ─────────────────────────────────────
        String s1 = new String("Temi");
        String s2 = new String("Temi");
        System.out.println(s1 == s2);        // false — different objects in memory
        System.out.println(s1.equals(s2));   // true — same content

    }
}


// ── No override — broken by default ──────────────────────────────────────────
class BrokenAccount {
    private String accountNumber;
    private String owner;

    BrokenAccount(String accountNumber, String owner) {
        this.accountNumber = accountNumber;
        this.owner = owner;
    }
}


// ── Only equals overridden — silent HashMap bug ───────────────────────────────
class HalfFixedAccount {
    private String accountNumber;
    private String owner;

    HalfFixedAccount(String accountNumber, String owner) {
        this.accountNumber = accountNumber;
        this.owner = owner;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        HalfFixedAccount other = (HalfFixedAccount) obj;
        return this.accountNumber.equals(other.accountNumber);
    }
    // hashCode NOT overridden — silent bug in HashMap
}


// ── Both overridden — correct ─────────────────────────────────────────────────
class BankAccount {
    private String accountNumber;
    private String owner;

    BankAccount(String accountNumber, String owner) {
        this.accountNumber = accountNumber;
        this.owner = owner;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        BankAccount other = (BankAccount) obj;
        return this.accountNumber.equals(other.accountNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountNumber); // same field as equals
    }
}
