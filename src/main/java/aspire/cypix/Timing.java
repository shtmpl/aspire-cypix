package aspire.cypix;

import java.util.concurrent.TimeUnit;

public class Timing {

    private static final int NANOSECONDS_IN_A_MILLISECOND = 1000000;

    /**
     * Sleeps the specified time in units.
     * Re-interrupts the current thread when the InterruptedException occurs
     */
    public static void sleep(long time, TimeUnit unit) {
        try {
            unit.sleep(time);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    public static void sleepForever() {
        while (true) {
            sleep(Long.MAX_VALUE, TimeUnit.DAYS);
        }
    }

    public static void timed(Runnable operation) {
        System.out.printf("Elapsed time: %s ms%n", formatElapsedTime(time(TimeUnit.NANOSECONDS, operation)));
    }

    public static long time(TimeUnit unit, Runnable operation) {
        long startTime = System.nanoTime();

        operation.run();

        long executionTime = System.nanoTime() - startTime;

        return convert(executionTime, TimeUnit.NANOSECONDS, unit);
    }

    private static long convert(long time, TimeUnit from, TimeUnit to) {
        // This function effectively addresses my inability to adequately comprehend this:
        return to.convert(time, from);
    }

    public static String formatElapsedTime(long nanoseconds) {
        return String.format("%d.%06d",
                nanoseconds / NANOSECONDS_IN_A_MILLISECOND,
                nanoseconds % NANOSECONDS_IN_A_MILLISECOND);
    }


}
