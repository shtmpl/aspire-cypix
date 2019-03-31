package aspire.cypix;

import java.util.Arrays;

public enum MyCandy implements Candy {
    C0(0),
    C1(1),
    C2(2),
    C3(3),
    C4(4);

    private final int flavour;

    MyCandy(int flavour) {
        this.flavour = flavour;
    }

    public int flavour() {
        return flavour;
    }

    public static MyCandy fromFlavour(int value) {
        return Arrays.stream(values())
                .filter((MyCandy it) -> it.flavour == value)
                .findFirst()
                .orElse(C0);
    }
}
