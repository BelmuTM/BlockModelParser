import java.util.Arrays;

public class Box {

    public Double[] size;
    public Double[] offset;
    public Double[] rotation;

    public Box(Double[] size, Double[] offset, Double[] rotation) {
        this.size     = size;
        this.offset   = offset;
        this.rotation = rotation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Box box = (Box) o;
        return Arrays.equals(size, box.size) && Arrays.equals(offset, box.offset) && Arrays.equals(rotation, box.rotation);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(size);
        result = 31 * result + Arrays.hashCode(offset);
        result = 31 * result + Arrays.hashCode(rotation);
        return result;
    }
}
