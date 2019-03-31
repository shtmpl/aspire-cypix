package aspire.cypix;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Mutex {

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    public Lock getLock() {
        return lock;
    }

    public Condition getCondition() {
        return condition;
    }
}
