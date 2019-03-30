package aspire.cypix;

import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class Main {

    private static final Random RANDOM = new Random();

    public static void main(String[] args) {
        ExecutorService executorService = Executors.newFixedThreadPool(100);

        CandyService service = new CandyService(IntStream
                .range(0, 10)
                .mapToObj(MyCandyEater::new)
                .toArray(MyCandyEater[]::new));

        System.out.println("Serving candies...");
//        arbitraryCandies()
//                .limit(100)
//                .forEach(service::addCandy);
        Stream.generate(() -> MyCandy.C1)
                .limit(100)
                .forEach(service::addCandy);

        executorService.shutdown();
        try {
            System.out.println("Awaiting termination...");
            executorService.awaitTermination(42, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    public static Stream<Candy> arbitraryCandies() {
        return RANDOM.ints(0, 5).mapToObj(MyCandy::fromValue);
    }
}
