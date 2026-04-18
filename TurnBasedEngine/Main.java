// Main.java
import java.util.*;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.println("=== Turn-Based Battle Engine (Rules-Driven) ===");
        System.out.println("Party: Warden, Priest, Raiju, Swamp");
        System.out.println();

        Game game = new Game(sc);
        game.run(); // loop handles retries/new battles
    }
}