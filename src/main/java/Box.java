import java.util.Arrays;

public class Box {

    public Double[] size;
    public Double[] offset;

    public Box(Double[] size, Double[] offset) {
        this.size   = size;
        this.offset = offset;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Box box = (Box) o;
        return Arrays.equals(size, box.size) && Arrays.equals(offset, box.offset);
    }
}
