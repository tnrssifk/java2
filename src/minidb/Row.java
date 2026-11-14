package minidb;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.ArrayList;
import java.util.List;


@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class Row {

    private List<Object> values;

    public Row() {
        this.values = new ArrayList<>();
    }

    public Row(List<Object> values) {
        this.values = new ArrayList<>(values);
    }

    public Object getValue(int index) {
        return values.get(index);
    }

    public void setValue(int index, Object value) {
        values.set(index, value);
    }

    public int size() {
        return values.size();
    }

    @Override
    public String toString() {
        return values.toString();
    }
}