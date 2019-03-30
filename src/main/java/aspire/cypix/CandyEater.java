package aspire.cypix;

/**
 * Candy consumer interface. The consumer can eat any candies in whatever way it founds appropriate.
 */
public interface CandyEater {

    /**
     * Eats candy.
     * This operation might take time.
     */
    void eat(Candy candy) throws Exception;
}
