package aspire.cypix;

import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class Main {

    private static final Random RANDOM = new Random();

    public static void main(String[] args) {
        CandyService service = new CandyService(IntStream
                .range(0, 10)
                .mapToObj(MyCandyEater::new)
                .toArray(MyCandyEater[]::new));

        System.out.println("Serving candies...");
        arbitraryCandies()
                .limit(100)
                .forEach(service::addCandy);

        System.out.println("Served candies");
    }

    public static Stream<Candy> arbitraryCandies() {
        return RANDOM.ints(0, 4).mapToObj(MyCandy::fromFlavour);
    }
}
