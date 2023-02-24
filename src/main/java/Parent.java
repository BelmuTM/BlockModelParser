import java.util.List;
import java.util.Objects;

public class Parent implements Comparable<Parent> {

    public Model model;
    public List<Model> children;

    public Parent(Model model, List<Model> children) {
        this.model     = model;
        this.children  = children;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Parent parent = (Parent) o;
        return Objects.equals(model, parent.model) && Objects.equals(children, parent.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(model, children);
    }

    @Override
    public int compareTo(Parent o) {
        return this.model.name.compareTo(o.model.name);
    }
}
