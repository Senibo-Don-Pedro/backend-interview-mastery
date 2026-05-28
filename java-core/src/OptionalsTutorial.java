import java.util.Optional;

public class OptionalsTutorial {
    public static void main(String[] args) throws Exception {

        // We try to find a cat, but our method currently returns an empty Optional
        Optional<Cat> optionalCat = findCatByName("Fred");

        // --- SECTION 1: orElse / orElseGet ---
        // Use case: Providing a default fallback value if the Optional is empty.
        // orElseGet is better for performance if creating the fallback is expensive (like new objects).
        Cat myCat = optionalCat.orElseGet(() -> new Cat("Default Cat", 0));
        System.out.println("Cat name: " + myCat.getName());


        // --- SECTION 2: ifPresent ---
        // Use case: You only want to do something IF the value exists. No fallback needed.
        optionalCat.ifPresent(cat -> 
            System.out.println("We found a cat named: " + cat.getName())
        );


        // --- SECTION 3: orElseThrow ---
        // Use case: The value is strictly required, and its absence is an error state.
        try {
            Cat requiredCat = optionalCat.orElseThrow(() -> new Exception("Cat not found!"));
        } catch (Exception e) {
            System.out.println("Exception caught: " + e.getMessage());
        }


        // --- SECTION 4: map with Optionals ---
        // Use case: Safely extract a property from an object inside an Optional.
        // If the cat is present, get the age. If not, default to -1.
        int age = optionalCat
            .map(cat -> cat.getAge())
            .orElse(-1);
            
        System.out.println("Cat's age (or -1 if missing): " + age);
    }
    
    private static Optional<Cat> findCatByName(String name) {
        // Simulating a database lookup that fails to find the cat
        return Optional.ofNullable(null); 
        
        // If it succeeded, it would look like: 
        // return Optional.of(new Cat(name, 3));
    }
}

class Cat {
    private String name;
    private int age;

    Cat(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() { return name; }
    public int getAge() { return age; }
}