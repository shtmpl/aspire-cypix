package aspire.cypix;

import java.util.Arrays;

public enum MyCandy implements Candy {
    C0(0),
    C1(1),
    C2(2),
    C3(3),
    C4(4);

    private final int value;

    MyCandy(int value) {
        this.value = value;
    }

    public int flavour() {
        return value;
    }

    public static MyCandy fromValue(int value) {
        return Arrays.stream(values())
                .filter((MyCandy it) -> it.value == value)
                .findFirst()
                .orElse(C0);
    }
}
