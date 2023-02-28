import java.util.Arrays;
import java.util.Objects;

public class Multipart extends Model {

    public String name;
    public Box[] boxes;

    public Multipart(String name, Box[] boxes) {
        super(name, boxes);
        this.name  = name;
        this.boxes = boxes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Multipart multipart = (Multipart) o;
        return Objects.equals(name, multipart.name) && Arrays.equals(boxes, multipart.boxes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(super.hashCode(), name);
        result = 31 * result + Arrays.hashCode(boxes);
        return result;
    }
}
