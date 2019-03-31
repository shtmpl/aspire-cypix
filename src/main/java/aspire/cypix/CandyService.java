package aspire.cypix;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Candy eating service.
 */
public class CandyService {

    private final ExecutorService producingExecutorService = Executors.newSingleThreadExecutor();
    private final Mutex producingMutex = new Mutex();
    private final LinkedList<Candy> producingQueue = new LinkedList<>();

    private final ExecutorService consumingExecutorService;
    private final Mutex consumingMutex = new Mutex();
    private final Map<Integer, Mutex> consumingMutexesByFlavour = new LinkedHashMap<>();
    private final Set<Integer> consumingFlavourQueue = new LinkedHashSet<>();

    /**
     * Initialised with an array of available consumers ({@link CandyEater}s).
     */
    public CandyService(CandyEater... candyEaters) {
        this.consumingExecutorService = Executors.newFixedThreadPool(candyEaters.length);

        Arrays.stream(candyEaters).forEach((CandyEater it) -> CompletableFuture.runAsync(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Candy candy;

                producingMutex.getLock().lock();
                try {
                    while (producingQueue.isEmpty() || (candy = producingQueue.removeFirst()) == null) {
                        producingMutex.getCondition().await();
                    }

                    System.err.printf("[%s]. Remaining: %s%n", candy, producingQueue);

                    producingMutex.getCondition().signalAll();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();

                    throw new RuntimeException(exception);
                } finally {
                    producingMutex.getLock().unlock();
                }

                int flavour = candy.flavour();

                Mutex consumingMutexByFlavour;

                consumingMutex.getLock().lock();
                try {
                    if (!consumingMutexesByFlavour.containsKey(flavour)) {
                        consumingMutexesByFlavour.put(flavour, new Mutex());
                    }

                    consumingMutexByFlavour = consumingMutexesByFlavour.get(flavour);
                } finally {
                    consumingMutex.getLock().unlock();
                }

                consumingMutexByFlavour.getLock().lock();
                try {
                    while (consumingFlavourQueue.contains(flavour)) {
                        consumingMutexByFlavour.getCondition().await();
                    }

                    consumingFlavourQueue.add(flavour);

                    try {
                        it.eat(candy);
                    } catch (Exception exception) {
                        /*NOP*/
                    } finally {
                        consumingFlavourQueue.remove(flavour);
                    }

                    consumingMutexByFlavour.getCondition().signalAll();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();

                    throw new RuntimeException(exception);
                } finally {
                    consumingMutexByFlavour.getLock().unlock();
                }
            }
        }, this.consumingExecutorService));
    }

    /**
     * Adds a candy for the available consumers ({@link CandyEater}s) to consume ({@link CandyEater#eat(Candy)}).
     */
    public void addCandy(Candy candy) {
        CompletableFuture.runAsync(() -> {
            producingMutex.getLock().lock();
            try {
                producingQueue.addLast(candy);
                optimiseQueue(producingQueue);

                producingMutex.getCondition().signalAll();
            } finally {
                producingMutex.getLock().unlock();
            }
        }, producingExecutorService);
    }

    private static void optimiseQueue(List<Candy> queue) {
        Map<Integer, List<Candy>> grouped = queue
                .stream()
                .collect(Collectors.groupingBy(Candy::flavour));

        List<Candy> ordered = interleave(grouped.entrySet()
                .stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList()));

        queue.clear();
        queue.addAll(ordered);
    }

    private static <X> List<X> interleave(List<List<X>> lists) {
        if (lists.isEmpty()) {
            return Collections.emptyList();
        }

        List<Iterator<X>> its = lists.stream().map(List::iterator).collect(Collectors.toList());

        List<X> result = new LinkedList<>();
        while (its.stream().anyMatch(Iterator::hasNext)) {
            its.forEach((Iterator<X> it) -> {
                if (it.hasNext()) {
                    result.add(it.next());
                }
            });
        }

        return result;
    }
}
