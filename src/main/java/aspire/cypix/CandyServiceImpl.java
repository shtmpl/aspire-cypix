package aspire.cypix;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
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
import java.util.stream.IntStream;

/**
 * ICandy eating service.
 * <p>
 * This implementation separates the processing logic into three layers.
 * <p>
 * Firstly, there is the supply layer.
 * This layer dispatches the inbound item (added via {@link CandyServiceBase#addCandy(ICandy)})
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
 * (each associated with a {@link ICandyEater} provided as an argument to the service constructor).
 * A consumer routine iteratively executes the following steps:
 * <ol>
 * <li>It takes the next available item from the consuming queue.</li>
 * <li>It processes the item obtained by calling ({@link ICandyEater#eat(ICandy)}).</li>
 * <li>It provides feedback about the operation completion via a consuming feedback queue.</li>
 * </ol>
 * </p>
 */
public class CandyServiceImpl extends CandyServiceBase {

    private final ExecutorService supplyingExecutorService = Executors.newFixedThreadPool(1);
    private final ExecutorService mediatingExecutorService = Executors.newFixedThreadPool(3);
    private final ExecutorService consumingExecutorService;

    private final BlockingQueue<ICandy> supplyingQueue = new LinkedBlockingDeque<>();
    private final BlockingQueue<ICandy> consumingQueue = new LinkedBlockingDeque<>();
    private final BlockingQueue<ICandy> consumingFeedback = new LinkedBlockingDeque<>();

    public CandyServiceImpl(ICandyEater... candyEaters) {
        super(candyEaters);

        CompletableFuture.runAsync(this::mediate, this.mediatingExecutorService);

        this.consumingExecutorService = Executors.newFixedThreadPool(candyEaters.length);
        Arrays.stream(candyEaters)
                .forEach((ICandyEater eater) -> CompletableFuture.runAsync(() -> consume(eater), this.consumingExecutorService));
    }

    @Override
    public void addCandy(ICandy candy) {
        CompletableFuture.runAsync(() -> supply(candy), supplyingExecutorService);
    }

    private void supply(ICandy candy) {
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
        LinkedList<ICandy> queue = new LinkedList<>();

        CompletableFuture.runAsync(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    ICandy candy = supplyingQueue.take();

                    lock.lock();
                    try {
                        queue.addLast(candy);

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
                    int index;
                    while ((index = findFirstIndexOf(queue, (ICandy it) -> !processing.containsKey(it.getCandyFlavour()))) == -1 ||
                            processing.containsKey(queue.get(index).getCandyFlavour())) {
                        condition.await();
                    }

                    ICandy next = queue.remove(index);
                    System.err.printf("%s <- %s%n", next, queue);

                    processing.put(next.getCandyFlavour(), true);
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
                    ICandy candy = consumingFeedback.take();
                    int flavour = candy.getCandyFlavour();

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

    private void consume(ICandyEater eater) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                ICandy candy = consumingQueue.take();

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

    private static <X> int findFirstIndexOf(List<X> list, Predicate<X> predicate) {
        return IntStream.range(0, list.size())
                .filter((int index) -> predicate.test(list.get(index)))
                .findFirst()
                .orElse(-1);
    }
}
