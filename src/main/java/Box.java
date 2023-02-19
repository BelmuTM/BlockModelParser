import java.util.Arrays;

public class Box {

    public Double[] size;
    public Double[] offset;
    public Double[] modelRotation;
    public Double[] boxRotation;
    public Double[] rotationOrigin;

    public Box(Double[] size, Double[] offset, Double[] modelRotation, Double[] boxRotation, Double[] rotationOrigin) {
        this.size           = size;
        this.offset         = offset;
        this.modelRotation  = modelRotation;
        this.boxRotation    = boxRotation;
        this.rotationOrigin = rotationOrigin;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Box box = (Box) o;
        return Arrays.equals(size, box.size) && Arrays.equals(offset, box.offset) && Arrays.equals(modelRotation, box.modelRotation) && Arrays.equals(boxRotation, box.boxRotation) && Arrays.equals(rotationOrigin, box.rotationOrigin);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(size);
        result = 31 * result + Arrays.hashCode(offset);
        result = 31 * result + Arrays.hashCode(modelRotation);
        result = 31 * result + Arrays.hashCode(boxRotation);
        result = 31 * result + Arrays.hashCode(rotationOrigin);
        return result;
    }
}
