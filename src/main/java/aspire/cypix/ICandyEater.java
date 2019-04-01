package aspire.cypix;

/**
 * ICandy consumer interface. The consumer can eat any candies in whatever way it founds appropriate.
 */
public interface ICandyEater {

    /**
     * Eats candy.
     * This operation might take time.
     */
    void eat(ICandy candy) throws Exception;
}
