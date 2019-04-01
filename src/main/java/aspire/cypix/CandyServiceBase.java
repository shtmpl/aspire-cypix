package aspire.cypix;

/**
 * Candy eating service.
 */
public abstract class CandyServiceBase {

    /**
     * Initialised with an array of available consumers ({@link CandyEater}s).
     */
    public CandyServiceBase(CandyEater... candyEaters) {
    }

    /**
     * Adds a candy for the available consumers ({@link CandyEater}s) to consume ({@link CandyEater#eat(Candy)}).
     */
    public abstract void addCandy(Candy candy);
}
