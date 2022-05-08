import java.util.Arrays;
import java.util.Objects;

public class Model {

    public String name;
    public Box[] boxes;
    public Integer[] rotation;

    public Model(String name, Box[] boxes, Integer[] rotation) {
        this.name     = name;
        this.boxes    = boxes;
        this.rotation = rotation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (getClass() != o.getClass()) return false;
        Model model = (Model) o;
        return Arrays.equals(boxes, model.boxes) && Arrays.equals(rotation, model.rotation);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(boxes);
        result = 31 * result + Arrays.hashCode(rotation);
        return result;
    }
}
