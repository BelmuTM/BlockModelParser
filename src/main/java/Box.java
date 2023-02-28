import java.util.Arrays;
import java.util.Objects;

public class Box {

    public Double[] size;
    public Double[] offset;
    public Double[] modelRotation;
    public Double[] boxRotation;
    public Double[] pivot;
    public int uvLock;

    public Box(Double[] size, Double[] offset, Double[] modelRotation, Double[] boxRotation, Double[] pivot, int uvLock) {
        this.size           = size;
        this.offset         = offset;
        this.modelRotation  = modelRotation;
        this.boxRotation    = boxRotation;
        this.pivot          = pivot;
        this.uvLock         = uvLock;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Box box = (Box) o;
        return uvLock == box.uvLock && Arrays.equals(size, box.size) && Arrays.equals(offset, box.offset) && Arrays.equals(modelRotation, box.modelRotation) && Arrays.equals(boxRotation, box.boxRotation) && Arrays.equals(pivot, box.pivot);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(uvLock);
        result = 31 * result + Arrays.hashCode(size);
        result = 31 * result + Arrays.hashCode(offset);
        result = 31 * result + Arrays.hashCode(modelRotation);
        result = 31 * result + Arrays.hashCode(boxRotation);
        result = 31 * result + Arrays.hashCode(pivot);
        return result;
    }
}
