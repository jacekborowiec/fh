package pl.fhframework.model.forms;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import pl.fhframework.annotations.Control;
import pl.fhframework.annotations.DesignerXMLProperty;
import pl.fhframework.annotations.DocumentedComponentAttribute;
import pl.fhframework.annotations.XMLProperty;
import pl.fhframework.core.util.StringUtils;

@Control(parents = {TablePaged.class,TableLazy.class})
public class ColumnLazy extends Column {

    @Getter
    @Setter
    private boolean sortable = false;

    @JsonIgnore
    @Getter
    @Setter
    @DocumentedComponentAttribute(value = "Property name passed in the Loaded object to be interpreted in a data source (eg. DAO)")
    @XMLProperty
    @DesignerXMLProperty(commonUse = true)
    private String sortBy;

    public ColumnLazy(Form form) {
        super(form);
    }

    public void init() {
        super.init();
        sortable = !StringUtils.isNullOrEmpty(sortBy) && !isSubColumnsExists();
    }
}
