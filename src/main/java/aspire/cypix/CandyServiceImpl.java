package aspire.cypix;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Candy eating service.
 * <p>
 * This implementation separates the processing logic into three layers.
 * <p>
 * Firstly, there is the supply layer.
 * This layer dispatches the inbound item (added via {@link CandyServiceBase#addCandy(Candy)})
 * to the supplying queue.
 * </p>
 * <p>
 * Secondly, there is the mediator layer.
 * This layer imposes the mediation logic between the supply layer and the consume layer.
 * The mediation logic includes (but is not limited to):
 * <ul>
 * <li>Accepting an item from a supplying queue.</li>
 * <li>Performing optimisations for the accumulated items accepted from a supplying queue.</li>
 * <li>Imposing rules and regulations upon the consumption logic.</li>
 * </ul>
 * </p>
 * <p>
 * Lastly, there is the consume layer.
 * This layer is composed of a set of consumer routines
 * (each associated with a {@link CandyEater} provided as an argument to the service constructor).
 * A consumer routine iteratively executes the following steps:
 * <ol>
 * <li>It takes the next available item from the consuming queue.</li>
 * <li>It processes the item obtained by calling ({@link CandyEater#eat(Candy)}).</li>
 * <li>It provides feedback about the operation completion via a consuming feedback queue.</li>
 * </ol>
 * </p>
 */
public class CandyServiceImpl extends CandyServiceBase {

    private final ExecutorService supplyingExecutorService = Executors.newFixedThreadPool(1);
    private final ExecutorService mediatingExecutorService = Executors.newFixedThreadPool(3);
    private final ExecutorService consumingExecutorService;

    private final BlockingQueue<Candy> supplyingQueue = new LinkedBlockingDeque<>();
    private final BlockingQueue<Candy> consumingQueue = new LinkedBlockingDeque<>();
    private final BlockingQueue<Candy> consumingFeedback = new LinkedBlockingDeque<>();

    /**
     * Initialised with an array of available consumers ({@link CandyEater}s).
     */
    public CandyServiceImpl(CandyEater... candyEaters) {
        super(candyEaters);

        CompletableFuture.runAsync(this::mediate, this.mediatingExecutorService);

        this.consumingExecutorService = Executors.newFixedThreadPool(candyEaters.length);
        Arrays.stream(candyEaters)
                .forEach((CandyEater eater) -> CompletableFuture.runAsync(() -> consume(eater), this.consumingExecutorService));
    }

    /**
     * Adds a candy for the available consumers ({@link CandyEater}s) to consume ({@link CandyEater#eat(Candy)}).
     */
    public void addCandy(Candy candy) {
        CompletableFuture.runAsync(() -> supply(candy), supplyingExecutorService);
    }

    private void supply(Candy candy) {
        try {
            supplyingQueue.put(candy);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new RuntimeException(exception);
        }
    }

    /**
     * Establishes a mediating logic between producers and consumers.
     */
    private void mediate() {
        ConcurrentMap<Integer, Boolean> processing = new ConcurrentSkipListMap<>();

        Lock lock = new ReentrantLock();
        Condition condition = lock.newCondition();
        LinkedList<Candy> queue = new LinkedList<>();

        CompletableFuture.runAsync(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Candy candy = supplyingQueue.take();

                    lock.lock();
                    try {
                        queue.addLast(candy);
                        optimiseQueue(queue);

                        condition.signalAll();
                    } finally {
                        lock.unlock();
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();

                    throw new RuntimeException();
                }
            }
        }, mediatingExecutorService);

        CompletableFuture.runAsync(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                lock.lock();
                try {
                    Candy candy;
                    while ((candy = queue.stream().findFirst().orElse(null)) == null ||
                            processing.containsKey(candy.flavour())) {
                        condition.await();
                    }

                    Candy next = queue.removeFirst();
                    System.err.printf("%s <- %s%n", next, queue);

                    processing.put(next.flavour(), true);
                    consumingQueue.put(next);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();

                    throw new RuntimeException(exception);
                } finally {
                    lock.unlock();
                }
            }
        }, mediatingExecutorService);

        CompletableFuture.runAsync(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Candy candy = consumingFeedback.take();
                    int flavour = candy.flavour();

                    processing.remove(flavour);

                    lock.lock();
                    try {
                        condition.signalAll();
                    } finally {
                        lock.unlock();
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();

                    throw new RuntimeException();
                }
            }
        }, mediatingExecutorService);
    }

    private void consume(CandyEater eater) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Candy candy = consumingQueue.take();

                try {
                    eater.eat(candy);
                } catch (Exception ignore) {
                    /*NOP*/
                }

                consumingFeedback.put(candy);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();

                throw new RuntimeException(exception);
            }
        }
    }

    /**
     * Optimises the queue by reordering its elements.
     * <p>
     * This implementation is inspired by the premise that
     * no two candies of a given kind could be consumed simultaneously by any two consumers.
     * The optimal order of elements is therefore considered to be the sequence of groups of diversified elements.
     * <p>
     * Long story short, we'd like the sequence of elements
     * {@code (0, 1, 1, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 4)}
     * be ordered like {@code (0, 1, 2, 3, 4, 1, 2, 3, 4, 2, 3, 4, 3, 4, 4)}.
     */
    private static void optimiseQueue(List<Candy> queue) {
        // FIXME: Current implementation is likely to be ineffective.
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
