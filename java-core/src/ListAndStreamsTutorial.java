import java.util.List;
import java.util.stream.Collectors;

public class ListAndStreamsTutorial {

  public static void main(String[] args) {
    
    List<String> names = List.of("Alice", "Bob", "Charlie", "David", "Eve");

    // --- SECTION 1: forEach (Terminal) ---
    // Use case: Executing an action (like printing) for every item. Returns nothing.
    System.out.println("--- forEach ---");
    names.forEach(n -> System.out.println(n.toUpperCase()));


    // --- SECTION 2: filter (Intermediate) ---
    // Use case: Narrowing down a list based on a true/false condition.
    // Note: .toList() is the preferred modern syntax over .collect(Collectors.toList())
    List<String> namesWithLetterA = names.stream()
        .filter(n -> n.startsWith("A"))
        .toList();
    
    System.out.println("\n--- filter ---");
    System.out.println(namesWithLetterA);


    // --- SECTION 3: map (Intermediate) ---
    // Use case: Transforming data from one form to another (e.g., String to Integer).
    List<Integer> namesLength = names.stream()
        .map(name -> name.length())
        .toList();
        
    System.out.println("\n--- map ---");
    System.out.println(namesLength);


    // --- SECTION 4: filter + map Chaining ---
    // Use case: Combining operations. ALWAYS filter before you map to save processing time.
    List<String> transformedList = names.stream()
        .filter(name -> name.length() > 3)
        .map(name -> name.toUpperCase())
        .toList();
        
    System.out.println("\n--- filter + map ---");
    System.out.println(transformedList);


    // --- SECTION 5: reduce (Terminal) ---
    // Use case: Taking a collection of items and condensing them into a single value (like a sum).
    Integer totalLength = names.stream()
        .map(name -> name.length())
        .reduce(0, (a, b) -> a + b); // 0 is the starting value
        
    System.out.println("\n--- reduce ---");
    System.out.println("Total characters in all names: " + totalLength);


    // --- SECTION 6: Lazy Evaluation Proof ---
    // This block will do absolutely NOTHING because it lacks a terminal operation (like .toList()).
    // If you add .toList() to the end, the print statements will suddenly appear.
    names.stream()
        .filter(name -> {
            System.out.println("filtering: " + name);
            return name.length() > 3;
        })
        .map(name -> name.toUpperCase());


    // --- SECTION 7: Working with Numbers ---
    List<Integer> numbers = List.of(3, 15, 7, 22, 9, 18);

    List<Integer> processedNumbers = numbers.stream()
        .filter(n -> n > 10) // Keeps 15, 22, 18
        .map(n -> n * 2)     // Doubles them: 30, 44, 36
        .collect(Collectors.toList()); // Older Java way to collect, .toList() is better now
  }
}