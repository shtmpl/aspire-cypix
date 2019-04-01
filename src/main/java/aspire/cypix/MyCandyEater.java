package aspire.cypix;

import java.sql.Date;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class MyCandyEater implements ICandyEater {

    private final int id;

    public MyCandyEater(int id) {
        this.id = id;
    }

    public void eat(ICandy candy) throws Exception {
        int flavour = candy.getCandyFlavour();

        say(String.format("OM NOM NOM'ing c%d", flavour));

        long elapsed = Timing.time(TimeUnit.NANOSECONDS, () -> eating(candy));

        say(String.format("OM NOM NOM'd c%d. Elapsed time: %s", flavour, Timing.formatElapsedTime(elapsed)));
    }

    private void eating(ICandy candy) {
        int flavour = candy.getCandyFlavour();
        switch (flavour) {
            case 0:
            case 1:
            case 2:
            case 3:
                Timing.sleep(flavour, TimeUnit.SECONDS);
                return;
            case 4:
                say("Boom!");
                Timing.sleepForever();
                return;
            default:
                Timing.sleep(10, TimeUnit.SECONDS);
        }
    }

    private void say(String something) {
        System.err.printf("[%s] [%s] %s%n", id, Date.from(Instant.now()), something);
    }
}
