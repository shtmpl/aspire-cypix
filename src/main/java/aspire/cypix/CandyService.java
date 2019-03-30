package aspire.cypix;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Candy eating service.
 */
public class CandyService {

    private final ExecutorService producingExecutorService = Executors.newSingleThreadExecutor();
    private final ExecutorService consumingExecutorService;

    private final Work work = new Work();
    private final Processing processing = new Processing();

    /**
     * Initialised with an array of available consumers ({@link CandyEater}s).
     */
    public CandyService(CandyEater... candyEaters) {
        this.consumingExecutorService = Executors.newFixedThreadPool(candyEaters.length);

        Arrays.stream(candyEaters).forEach((CandyEater it) -> CompletableFuture.runAsync(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Candy candy;

                work.availabilityLock.lock();
                try {
                    while ((candy = work.stealAnyCandy()) == null) {
                        work.availabilityCondition.await();
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();

                    throw new RuntimeException(exception);
                } finally {
                    work.availabilityLock.unlock();
                }

                int flavour = candy.flavour();

                processing.lock.lock();
                Processing.Mutex mutex;
                try {
                    mutex = processing.mutexFor(flavour);
                } finally {
                    processing.lock.unlock();
                }

                mutex.lock.lock();
                try {
                    while (processing.isOngoingFor(flavour)) {
                        mutex.condition.await();
                    }

                    processing.conjFor(flavour);

                    try {
                        it.eat(candy);
                    } catch (Exception exception) {
                        /*NOP*/
                    }

                    processing.disjFor(flavour);
                    mutex.condition.signalAll();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();

                    throw new RuntimeException(exception);
                } finally {
                    mutex.lock.unlock();
                }
            }
        }, this.consumingExecutorService));
    }

    /**
     * Adds a candy for the available consumers ({@link CandyEater}s) to consume ({@link CandyEater#eat(Candy)}).
     */
    public void addCandy(Candy candy) {
        CompletableFuture.runAsync(() -> {
            work.availabilityLock.lock();
            try {
                int flavour = candy.flavour();
                if (!work.available.containsKey(flavour)) {
                    work.available.put(flavour, new LinkedList<>());
                }

                work.available.get(flavour).add(candy);
                work.availabilityCondition.signalAll();
            } finally {
                work.availabilityLock.unlock();
            }
        }, producingExecutorService);
    }

    private static class Work {

        private final Lock availabilityLock = new ReentrantLock();
        private final Condition availabilityCondition = availabilityLock.newCondition();

        private final Map<Integer, List<Candy>> available = new LinkedHashMap<>();

        public Candy stealAnyCandy() {
            for (List<Candy> candies : available.values()) {
                if (candies.isEmpty()) {
                    continue;
                }

                return candies.remove(0);
            }

            return null;
        }
    }

    private static class Processing {

        private final Lock lock = new ReentrantLock();

        private final Set<Integer> ongoing = new LinkedHashSet<>();
        private final Map<Integer, Mutex> mutexMap = new LinkedHashMap<>();

        public boolean isOngoingFor(int flavour) {
            return ongoing.contains(flavour);
        }

        public void conjFor(int flavour) {
            this.ongoing.add(flavour);
        }

        public void disjFor(int flavour) {
            this.ongoing.remove(flavour);
        }

        public Mutex mutexFor(int flavour) {
            if (!mutexMap.containsKey(flavour)) {
                mutexMap.put(flavour, new Mutex());
            }

            return mutexMap.get(flavour);
        }

        private static class Mutex {

            private final Lock lock = new ReentrantLock();
            private final Condition condition = lock.newCondition();

        }
    }
}
