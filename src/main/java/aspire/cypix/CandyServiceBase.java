package aspire.cypix;

/**
 * ICandy eating service.
 */
public abstract class CandyServiceBase {

    /**
     * Initialised with an array of available consumers ({@link ICandyEater}s).
     */
    public CandyServiceBase(ICandyEater... candyEaters) {
    }

    /**
     * Adds a candy for the available consumers ({@link ICandyEater}s) to consume ({@link ICandyEater#eat(ICandy)}).
     */
    public abstract void addCandy(ICandy candy);
}
