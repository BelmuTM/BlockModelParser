import java.util.Arrays;

public class Model {

    public String name;
    public Box[] boxes;

    public Model(String name, Box[] boxes) {
        this.name  = name;
        this.boxes = boxes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (getClass() != o.getClass()) return false;
        Model model = (Model) o;
        return Arrays.equals(boxes, model.boxes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(boxes);
    }
}
